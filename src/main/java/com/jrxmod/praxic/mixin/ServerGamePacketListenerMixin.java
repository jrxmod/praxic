package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.manager.CheckManager;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * Grace ticks granted to TeleportCheck after the client confirms a
     * server-initiated teleport (ender pearl, chorus fruit, /tp, portal,
     * respawn). Covers the tick where the large position jump becomes visible
     * plus network round-trip for the confirmation packet.
     */
    private static final int TELEPORT_GRACE_TICKS = 20;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        boolean hasPos = packet.hasPosition();
        boolean onGroundPacket = packet.isOnGround();

        player.getServer().execute(() -> {
            CheckManager cm = Praxic.getCheckManager();
            PlayerData data = cm.getPlayerData(player.getUUID());
            if (data == null) return;

            cm.getBadPacketsCheck().onMovePacket(player, packet, data);

            // Ground spoof detection uses onGround flag from packet
            data.lastPacketOnGround = onGroundPacket;
            data.lastPacketHasPos = hasPos;

            if (!hasPos) return;

            cm.getTimerCheck().onMovePacket(player, data);
            cm.getTeleportCheck().onMovePacket(player, packet, data);
        });
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void onHandlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        var pos = packet.getPos();
        player.getServer().execute(() -> {
            PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
            if (data == null) return;
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                Praxic.getCheckManager().getFastBreakCheck().onStartBreak(player, pos, data);
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                Praxic.getCheckManager().getFastBreakCheck().onStopBreak(player, pos, data);
            }
        });
    }

    @Inject(method = "handleInteract", at = @At("HEAD"))
    private void onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        Entity target = packet.getTarget(player.serverLevel());
        if (target == null) return;
        AtomicBoolean isAttack = new AtomicBoolean(false);
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) {}
            @Override public void onInteraction(InteractionHand hand, Vec3 pos) {}
            @Override public void onAttack() { isAttack.set(true); }
        });
        if (!isAttack.get()) return;
        var targetUuid = target.getUUID();
        player.getServer().execute(() -> {
            CheckManager cm = Praxic.getCheckManager();
            PlayerData data = cm.getPlayerData(player.getUUID());
            if (data == null) return;
            data.lastAttackTime = System.currentTimeMillis();

            if (Praxic.getGhostEntityManager() != null
                    && Praxic.getGhostEntityManager().onPlayerAttack(player, targetUuid, data)) {
                return;
            }

            cm.getCriticalsCheck().checkAttack(player, target, data);
            cm.getReachCheck().checkAttack(player, target, data);
            cm.getKillAuraCheck().checkAttack(player, target, data);
            cm.getAutoClickerCheck().onAttack(player, data);
        });
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"))
    private void onHandleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        player.getServer().execute(() -> {
            PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
            if (data == null) return;
            if (player.getMainHandItem().getItem() instanceof FireworkRocketItem
                    || player.getOffhandItem().getItem() instanceof FireworkRocketItem) {
                data.lastRocketUseTime = System.currentTimeMillis();
            }
        });
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"))
    private void onHandleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        player.getServer().execute(() -> {
            PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
            if (data == null) return;
            Praxic.getCheckManager().getInventoryCheck().onInventoryClick(player, data);
        });
    }

    /**
     * Marks a legitimate server-initiated teleport so TeleportCheck does not
     * mistake the resulting position jump for a Blink / Teleport cheat.
     */
    @Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"))
    private void onHandleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        player.getServer().execute(() -> {
            PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
            if (data != null) {
                data.teleportGraceTicks = TELEPORT_GRACE_TICKS;
            }
        });
    }
}

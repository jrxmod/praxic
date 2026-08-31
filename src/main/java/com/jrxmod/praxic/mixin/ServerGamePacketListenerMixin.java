package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.physics.ImpulseEngine;
import com.jrxmod.praxic.manager.CheckManager;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.WindChargeItem;
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

    /**
     * Packet handlers may first run off-thread and then be re-dispatched.
     * Cancellation is only valid on the server thread; returning without
     * cancel lets vanilla reschedule the packet.
     */
    private boolean praxic$onServerThread() {
        return player.getServer() != null && player.getServer().isSameThread();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void onHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;

        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;

        cm.getBadPacketsCheck().onMovePacket(player, packet, data);

        data.lastPacketOnGround = packet.isOnGround();
        data.lastPacketHasPos = packet.hasPosition();

        cm.getNoFallCheck().onMovePacket(player, packet, data);
        cm.getGroundSpoofCheck().onMovePacket(player, packet, data);

        if (!packet.hasPosition() && packet.hasRotation()) {
            data.lastLookOnlyMs = System.currentTimeMillis();
        }

        if (!packet.hasPosition()) return;

        cm.getTimerCheck().onMovePacket(player, data);
        cm.getFlyCheck().onMovePacket(player, packet, data);
        cm.getSpeedCheck().onMovePacket(player, packet, data);
        cm.getNoSlowCheck().onMovePacket(player, packet, data);
        cm.getJesusCheck().onMovePacket(player, packet, data);
        cm.getStepCheck().onMovePacket(player, packet, data);
        cm.getTeleportCheck().onMovePacket(player, packet, data);

        if (Praxic.getMitigationEngine() != null
                && Praxic.getMitigationEngine().tryRejectMove(
                        player, data, packet,
                        cm.getFlyCheck(), cm.getSpeedCheck(), cm.getTeleportCheck())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void onHandleMoveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        cm.getVehicleFlyCheck().onVehiclePacket(player, packet, data);
        if (cm.getBoatFlyCheck().onVehiclePacket(player, packet, data)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void onHandlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        var pos = packet.getPos();
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            Praxic.getCheckManager().getFastBreakCheck().onStartBreak(player, pos, data);
        } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            Praxic.getCheckManager().getFastBreakCheck().onStopBreak(player, pos, data);
        } else if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            Praxic.getCheckManager().getAutoTotemCheck().onOffhandSwap(player, data);
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        Entity target = packet.getTarget(player.serverLevel());
        if (target == null) return;
        AtomicBoolean isAttack = new AtomicBoolean(false);
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) {}
            @Override public void onInteraction(InteractionHand hand, Vec3 pos) {}
            @Override public void onAttack() { isAttack.set(true); }
        });
        if (!isAttack.get()) return;

        CheckManager cm = Praxic.getCheckManager();
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        data.lastAttackTime = System.currentTimeMillis();

        if (Praxic.getGhostEntityManager() != null
                && Praxic.getGhostEntityManager().onPlayerAttack(player, target.getUUID(), data)) {
            return;
        }

        cm.getCriticalsCheck().checkAttack(player, target, data);
        boolean cancelReach = cm.getReachCheck().checkAttack(player, target, data);
        boolean cancelMace = cm.getMaceSmashCheck().checkAttack(player, target, data);
        cm.getKillAuraCheck().checkAttack(player, target, data);
        cm.getAutoClickerCheck().onAttack(player, data);

        boolean whitelisted = Praxic.getWhitelistManager() != null
                && Praxic.getWhitelistManager().isWhitelisted(player.getUUID());
        if (!whitelisted && (cancelReach || cancelMace)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void onHandleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;

        Item used = player.getItemInHand(packet.getHand()).getItem();
        data.lastItemUseTime = System.currentTimeMillis();
        if (player.getMainHandItem().getItem() instanceof FireworkRocketItem
                || player.getOffhandItem().getItem() instanceof FireworkRocketItem) {
            data.lastRocketUseTime = System.currentTimeMillis();
        }
        if (used instanceof WindChargeItem) {
            if (cm.getWindChargeAbuseCheck().onUse(player, data, used)) {
                ci.cancel();
                return;
            }
            if (Praxic.getImpulseEngine() != null) {
                Praxic.getImpulseEngine().record(player.getUUID(), ImpulseEngine.Kind.WIND);
            }
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"))
    private void onHandleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;
        CheckManager cm = Praxic.getCheckManager();
        cm.getInventoryCheck().onInventoryClick(player, data);
        cm.getAutoTotemCheck().onContainerClick(player, data, packet);
    }

    /**
     * Creative-style Fly sends flying=true without mayfly. Cancel and flag.
     */
    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void onHandlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        if (!packet.isFlying()) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        if (cm.getFlyCheck().onIllegalFlyingFlag(player, data)) {
            ci.cancel();
        }
    }

    /**
     * Marks a legitimate server-initiated teleport so TeleportCheck does not
     * mistake the resulting position jump for a Blink / Teleport cheat.
     */
    @Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"))
    private void onHandleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        if (!praxic$onServerThread()) return;
        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data != null) {
            data.teleportGraceTicks = TELEPORT_GRACE_TICKS;
        }
    }
}

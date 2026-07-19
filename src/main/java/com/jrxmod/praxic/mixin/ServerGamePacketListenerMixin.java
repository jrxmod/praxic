package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.AutoClickerCheck;
import com.jrxmod.praxic.checks.FastBreakCheck;
import com.jrxmod.praxic.checks.InventoryCheck;
import com.jrxmod.praxic.checks.KillAuraCheck;
import com.jrxmod.praxic.checks.ReachCheck;
import com.jrxmod.praxic.checks.ScaffoldCheck;
import com.jrxmod.praxic.checks.TimerCheck;
import com.jrxmod.praxic.engine.trap.GhostEntityManager;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
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

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {

        // Only count packets that carry position data — ignore rotation-only and status-only
        if (!packet.hasPosition()) return;

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        // Schedule on server thread — packet arrives on Netty IO thread
        player.getServer().execute(() -> {
            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof TimerCheck)
                    .map(c -> (TimerCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.onMovePacket(player, data));
        });
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void onHandlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        ServerboundPlayerActionPacket.Action action = packet.getAction();

        // Schedule on server thread — packet arrives on Netty IO thread
        player.getServer().execute(() -> {
            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof FastBreakCheck)
                    .map(c -> (FastBreakCheck) c)
                    .findFirst()
                    .ifPresent(check -> {
                        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                            check.onStartBreak(player, packet.getPos(), data);
                        } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                            check.onStopBreak(player, packet.getPos(), data);
                        }
                    });
        });
    }

    @Inject(method = "handleInteract", at = @At("HEAD"))
    private void onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {

        Entity target = packet.getTarget(player.serverLevel());
        if (target == null) return;

        AtomicBoolean isAttack = new AtomicBoolean(false);
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {}

            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {}

            @Override
            public void onAttack() {
                isAttack.set(true);
            }
        });

        if (!isAttack.get()) return;

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        // Schedule checks on the server thread to avoid duplicate execution
        // and thread-safety issues from Netty IO thread
        player.getServer().execute(() -> {
            // Ghost honeypot detection (KillAura trap)
            GhostEntityManager gem = Praxic.getGhostEntityManager();
            if (gem != null && gem.onPlayerAttack(player, target.getUUID())) {
                // Ghost hit — definitive evidence, handled by GhostEntityManager
                return;
            }

            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof ReachCheck)
                    .map(c -> (ReachCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.checkAttack(player, target, data));

            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof KillAuraCheck)
                    .map(c -> (KillAuraCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.checkAttack(player, target, data));

            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof AutoClickerCheck)
                    .map(c -> (AutoClickerCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.onAttack(player, data));
        });
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void onHandleUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        BlockHitResult hitResult = packet.getHitResult();

        // Schedule on server thread — packet arrives on Netty IO thread
        player.getServer().execute(() -> {
            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof ScaffoldCheck)
                    .map(c -> (ScaffoldCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.onBlockPlace(player, hitResult.getBlockPos(), data));
        });
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"))
    private void onHandleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        // Schedule on server thread — packet arrives on Netty IO thread
        player.getServer().execute(() -> {
            Praxic.getCheckManager().getChecks().stream()
                    .filter(c -> c instanceof InventoryCheck)
                    .map(c -> (InventoryCheck) c)
                    .findFirst()
                    .ifPresent(check -> check.onInventoryClick(player, data));
        });
    }
}

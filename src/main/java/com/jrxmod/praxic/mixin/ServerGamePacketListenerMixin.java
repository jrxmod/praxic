package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.KillAuraCheck;
import com.jrxmod.praxic.checks.ReachCheck;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
        });
    }
}

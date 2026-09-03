package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.CheckManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void praxic$onTotemPop(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        ServerPlayer player = praxic$asPlayer();
        if (player == null) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        cm.getAutoTotemCheck().onTotemPop(player, data);
    }

    @Inject(method = "hurt", at = @At("RETURN"), require = 0)
    private void praxic$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        ServerPlayer player = praxic$asPlayer();
        if (player == null) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        cm.getAutoTotemCheck().onDamage(player, data);
        cm.getVelocityCheck().onHurt(player, data, source);
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void praxic$onUseCompleted(CallbackInfo ci) {
        ServerPlayer player = praxic$asPlayer();
        if (player == null) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        cm.getFastUseCheck().onUseCompleted(player, data);
    }

    private ServerPlayer praxic$asPlayer() {
        if ((Object) this instanceof ServerPlayer player) return player;
        return null;
    }
}

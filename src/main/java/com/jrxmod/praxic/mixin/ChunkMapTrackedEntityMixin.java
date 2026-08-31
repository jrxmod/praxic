package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restricts honeypot ArmorStands to the suspected player's tracker.
 * Other players never receive spawn or update packets, so the stand cannot
 * collide with them or appear in ESP of bystanders.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ChunkMapTrackedEntityMixin {

    @Shadow
    @Final
    Entity entity;

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void praxic$hideGhostFromOthers(ServerPlayer player, CallbackInfo ci) {
        if (entity == null || player == null) return;
        if (Praxic.getGhostEntityManager() != null
                && Praxic.getGhostEntityManager().shouldHideFrom(entity, player)) {
            ci.cancel();
        }
    }
}

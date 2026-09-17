package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.physics.PhysicsResult;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;

/**
 * Detects fly / hover by comparing actual Y to PhysicsEngine prediction.
 * Thin rule - all physics simulation lives in PhysicsEngine / PhysicsResult.
 */
public class YPredictionCheck extends AbstractCheck {

    private static final long COOLDOWN_MS = 1500L;

    @Override
    public String getName() {
        return "YPredictionCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().yPredictionCheckEnabled) return;

        // Skip creative / spectator
        if (player.getAbilities().mayfly) return;

        // Skip passengers  -  vehicle physics differ entirely
        if (player.isPassenger()) return;

        // Water entry/resurface and hurt (knockback, totem pop) disrupt the
        // trajectory in ways the predictor is not seeded for. The fluid check
        // also covers the surface: there the feet block is air but the block
        // below is water, and bobbing / a push (e.g. a mob) keeps the player
        // level while the predictor expects a fall.
        if (player.isInWater() || player.isInLava() || data.wasInWater
                || atFluidSurface(player)) return;
        if (data.lastInWaterMs > 0
                && System.currentTimeMillis() - data.lastInWaterMs < 3000L) return;
        if (player.hurtTime > 0) return;
        // Entity pushes (players, mobs, water creatures) alter the trajectory
        // without setting hurtTime; skip while something touches nearby.
        if (touchedByEntity(player)) return;

        // Skip elytra gliding  -  trajectory is not gravity-driven
        if (player.isFallFlying()) return;

        // Skip effects that alter gravity - PhysicsEngine does not import MobEffects
        if (player.hasEffect(MobEffects.LEVITATION))   return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;
        if (player.isAutoSpinAttack()) return;
        if (Praxic.getImpulseEngine() != null && Praxic.getImpulseEngine().isActive(player.getUUID())) return;

        // Read physics result produced by PhysicsEngine this tick
        PhysicsResult physics = Praxic.getCheckManager()
                .getPhysicsResult(player.getUUID());

        // Not ready yet (first tick, grace, transition)
        if (physics == null || !physics.predictionActive) return;

        // Flag only when player is ABOVE prediction - fly / hover
        // Being below is legitimate (collisions, steps, slabs, etc.)
        if (physics.yDelta > physics.yTolerance) {
            if (!data.canFlag(getName(), COOLDOWN_MS)) return;

            ViolationManager.flag(player, data, this,
                String.format("deltaY=+%.3f predicted=%.3f actual=%.3f tol=%.3f ping=%dms",
                    physics.yDelta,
                    physics.predictedY,
                    physics.actualY,
                    physics.yTolerance,
                    Math.min(player.connection.latency(), 500)));
        }
    }

    /** True when liquid occupies the feet block or the block below it. */
    private static boolean atFluidSurface(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.core.BlockPos feet = player.blockPosition();
        return !player.level().getFluidState(feet).isEmpty()
                || !player.level().getFluidState(feet.below()).isEmpty();
    }

    /** True when a non-item entity touches the player's bounding box vicinity. */
    private static boolean touchedByEntity(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(2.5);
        for (net.minecraft.world.entity.Entity e : player.level()
                .getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                        box, e -> e != player)) {
            if (!(e instanceof net.minecraft.world.entity.item.ItemEntity)) return true;
        }
        return false;
    }
}

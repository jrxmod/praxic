package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.physics.PhysicsResult;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;

/**
 * Detects fly / hover by comparing actual Y to PhysicsEngine prediction.
 * Thin rule — all physics simulation lives in PhysicsEngine / PhysicsResult.
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

        // Skip passengers — vehicle physics differ entirely
        if (player.isPassenger()) return;

        // Skip elytra gliding — trajectory is not gravity-driven
        if (player.isFallFlying()) return;

        // Skip effects that alter gravity — PhysicsEngine does not import MobEffects
        if (player.hasEffect(MobEffects.LEVITATION))   return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;

        // Read physics result produced by PhysicsEngine this tick
        PhysicsResult physics = Praxic.getCheckManager()
                .getPhysicsResult(player.getUUID());

        // Not ready yet (first tick, grace, transition)
        if (physics == null || !physics.predictionActive) return;

        // Flag only when player is ABOVE prediction — fly / hover
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
}

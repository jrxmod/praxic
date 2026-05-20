package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;

public class YPredictionCheck extends AbstractCheck {

    // Minecraft gravity and drag constants (vanilla physics)
    private static final double GRAVITY = 0.08;
    private static final double DRAG    = 0.98;

    // Tolerance raised to 0.55 — vanilla sprint-jump divergence can reach ~0.5
    private static final double BASE_TOLERANCE = 1.0;

    // Extra tolerance per 500ms ping, scaled linearly, capped
    private static final double LAG_TOLERANCE_SCALE = 0.4;

    // Grace ticks after transition from grounded/water/climb state
    // Increased to 10 — gives predictor enough ticks to stabilize after landing/jump
    private static final int TRANSITION_GRACE_TICKS = 10;

    // Minimum cooldown between flags (ms) — raised to reduce log spam
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

        // Skip effects that alter gravity
        if (player.hasEffect(MobEffects.LEVITATION))   return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;

        MovementState state = data.movementState;

        // ── Reset on grounded / liquid / climbable states ──────────────────
        if (state == MovementState.GROUND
                || state == MovementState.WATER
                || state == MovementState.CLIMB) {
            data.predictedVY           = 0.0;
            data.yPredictionActive     = false;
            data.yPredictionGraceTicks = 0;
            return;
        }

        // ── Resync on knockback — hurt changes trajectory unpredictably ──────
        if (player.hurtTime > 0) {
            data.predictedVY       = player.getY() - data.prevY;
            data.yPredictionActive = true;
            return;
        }

        // ── Grace after transition from non-airborne state ──────────────────
        boolean justBecameAirborne =
            data.prevMovementState == MovementState.GROUND
            || data.prevMovementState == MovementState.WATER
            || data.prevMovementState == MovementState.CLIMB;

        if (justBecameAirborne && !data.yPredictionActive) {
            data.yPredictionGraceTicks = TRANSITION_GRACE_TICKS;
        }

        if (data.yPredictionGraceTicks > 0) {
            data.yPredictionGraceTicks--;
            // Seed predictor from real movement during grace — no comparison
            data.predictedVY       = player.getY() - data.prevY;
            data.yPredictionActive = true;
            return;
        }

        // ── First active tick: seed and return ──────────────────────────────
        if (!data.yPredictionActive) {
            data.predictedVY       = player.getY() - data.prevY;
            data.yPredictionActive = true;
            return;
        }

        // ── Simulate Minecraft Y-physics ────────────────────────────────────
        // vy_next = (vy_prev - GRAVITY) * DRAG
        // y_next  = y_prev + vy_next
        double nextVY    = (data.predictedVY - GRAVITY) * DRAG;
        double expectedY = data.prevY + nextVY;
        double actualY   = player.getY();
        double delta     = actualY - expectedY;

        // Advance predictor regardless of outcome
        data.predictedVY = nextVY;

        // ── Lag-compensated tolerance ───────────────────────────────────────
        int ping = player.connection.latency();
        int clampedPing = Math.min(ping, 500);
        double tolerance = BASE_TOLERANCE + (clampedPing / 500.0) * LAG_TOLERANCE_SCALE;

        // Flag only when player is ABOVE prediction — fly / hover
        // Being below is legitimate (collisions, steps, slabs, etc.)
        if (delta > tolerance) {
            // Resync after flag to prevent VL cascade
            data.predictedVY = actualY - data.prevY;

            if (!data.canFlag(getName(), COOLDOWN_MS)) return;

            ViolationManager.flag(player, data, this,
                String.format("deltaY=+%.3f expected=%.3f actual=%.3f tol=%.3f ping=%dms",
                    delta, expectedY, actualY, tolerance, ping));
        }
    }
}

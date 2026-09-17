package com.jrxmod.praxic.engine.physics;

import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.data.PlayerSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simulates Minecraft physics per player per tick.
 * Produces PhysicsResult consumed by detection checks.
 *
 * Y-prediction logic migrated from YPredictionCheck.
 * XZ prediction applies ground / air / water friction to last tick speed.
 */
public class PhysicsEngine {

    // -------------------------------------------------------------------------
    // Y-Prediction constants
    // -------------------------------------------------------------------------

    /** Base vertical tolerance in blocks. Final value after 4 test rounds. */
    private static final double BASE_TOLERANCE = 1.0;

    /**
     * Additional tolerance per 500 ms of ping.
     * At ping=500ms: total tolerance = BASE_TOLERANCE + LAG_TOLERANCE_SCALE.
     */
    private static final double LAG_TOLERANCE_SCALE = 0.4;

    /**
     * Grace ticks after a state transition (water-exit, climb-exit, first seed).
     * Predictor reseeds from real data; no delta comparison.
     */
    private static final int TRANSITION_GRACE_TICKS = 10;

    /**
     * Grace ticks after hurtTime - vertical knockback trajectory is unpredictable
     * and lasts longer than a regular state transition.
     */
    private static final int HURT_GRACE_TICKS = 20;

    // -------------------------------------------------------------------------
    // Per-player predictor state
    // -------------------------------------------------------------------------

    private final Map<UUID, Double>  predictedVY      = new HashMap<>();
    private final Map<UUID, Boolean> predictionActive = new HashMap<>();
    private final Map<UUID, Integer> graceTicks       = new HashMap<>();

    // -------------------------------------------------------------------------
    // Per-player horizontal predictor state
    // -------------------------------------------------------------------------

    private final Map<UUID, Double> prevSpeed = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs physics simulation for one player tick.
     * Call after SnapshotBuilder.build(), before checks run.
     */
    public PhysicsResult simulate(UUID uuid, PlayerSnapshot snapshot, PlayerData data) {
        PhysicsResult yResult  = simulateY(uuid, snapshot, data);
        PhysicsResult xzResult = simulateXZ(uuid, snapshot);

        return new PhysicsResult(
                yResult.predictedY,
                yResult.actualY,
                yResult.nextPredictedVY,
                yResult.predictionActive,
                yResult.yTolerance,
                xzResult.predictedSpeed,
                xzResult.actualSpeed
        );
    }

    /** Removes all state for a player on disconnect or death reset. */
    public void reset(UUID uuid) {
        resetY(uuid);
        prevSpeed.remove(uuid);
    }

    /** Clears vertical predictor only so XZ friction state survives ground ticks. */
    private void resetY(UUID uuid) {
        predictedVY.remove(uuid);
        predictionActive.remove(uuid);
        graceTicks.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Y simulation
    // -------------------------------------------------------------------------

    private PhysicsResult simulateY(UUID uuid, PlayerSnapshot snapshot, PlayerData data) {
        double actualY  = snapshot.y;
        double prevY    = snapshot.prevY;
        double actualDY = snapshot.dy;
        int    ping     = snapshot.ping;
        MovementState curr = snapshot.movementState;
        MovementState prev = snapshot.prevMovementState;

        boolean active = predictionActive.getOrDefault(uuid, false);
        int     grace  = graceTicks.getOrDefault(uuid, 0);
        double  vy     = predictedVY.getOrDefault(uuid, 0.0);

        // Guards - skip entirely, reset predictor
        boolean shouldSkip = snapshot.fallFlying
                          || snapshot.passenger
                          || snapshot.health <= 0
                          || data.joinGraceTicks > 0;

        if (shouldSkip) {
            resetY(uuid);
            return inactiveResult(actualY, actualY, 0.0, 0.0);
        }

        // hurtTime > 0 - any damage disrupts vertical trajectory unpredictably.
        // Use longer grace than a regular transition - knockback lasts longer.
        if (snapshot.hurtTime > 0) {
            reseed(uuid, actualDY);
            graceTicks.put(uuid, HURT_GRACE_TICKS);
            return inactiveResult(prevY + vy, actualY, vy, toleranceFor(ping));
        }

        // Ground / water / climb - reset predictor, nothing to predict
        if (curr == MovementState.GROUND
         || curr == MovementState.WATER
         || curr == MovementState.CLIMB) {
            resetY(uuid);
            return inactiveResult(actualY, actualY, 0.0, 0.0);
        }

        // Upward motion  -  a jump, or a landing + jump pair processed in one
        // server tick. The falling predictor must not compare against a rising
        // actual Y; reseed and either keep the running transition grace
        // (decrement it so it expires) or start a fresh one.
        if (actualDY > 0.0) {
            if (grace > 0) {
                graceTicks.put(uuid, grace - 1);
            } else {
                graceTicks.put(uuid, TRANSITION_GRACE_TICKS);
            }
            reseed(uuid, actualDY);
            return inactiveResult(prevY + actualDY, actualY, actualDY, toleranceFor(ping));
        }

        // Transition into air from ground/water/climb  -  seed and grace
        boolean transition = (prev == MovementState.GROUND
                           || prev == MovementState.WATER
                           || prev == MovementState.CLIMB)
                          && (curr == MovementState.JUMP
                           || curr == MovementState.AIR
                           || curr == MovementState.FALLING);
        if (transition || !active) {
            reseed(uuid, actualDY);
            graceTicks.put(uuid, TRANSITION_GRACE_TICKS);
            return inactiveResult(prevY + actualDY, actualY, actualDY, toleranceFor(ping));
        }

        // Decrement grace - reseed each grace tick, no comparison
        if (grace > 0) {
            graceTicks.put(uuid, grace - 1);
            reseed(uuid, actualDY);
            return inactiveResult(prevY + actualDY, actualY, actualDY, toleranceFor(ping));
        }

        // Active prediction - simulate one step of MC gravity
        // nextVY = (vy - 0.08) * 0.98
        double nextVY     = (vy - 0.08) * 0.98;
        double predictedY = prevY + nextVY;
        double tolerance  = toleranceFor(ping);

        predictedVY.put(uuid, nextVY);

        return new PhysicsResult(
                predictedY, actualY,
                nextVY,
                true, tolerance,
                0.0, 0.0
        );
    }

    // -------------------------------------------------------------------------
    // XZ simulation
    // -------------------------------------------------------------------------

    /**
     * Predicts horizontal speed from last tick using vanilla-like friction.
     * Ground 0.6, air 0.91, water 0.8. Input acceleration is not modelled;
     * the value is a decay floor that SpeedCheck compares against.
     */
    private PhysicsResult simulateXZ(UUID uuid, PlayerSnapshot snapshot) {
        double actual = snapshot.speed;
        double prev = prevSpeed.getOrDefault(uuid, actual);
        double friction = 0.91;
        if (snapshot.inWater) friction = 0.8;
        else if (snapshot.onGround) friction = 0.6;
        double predicted = prev * friction;
        prevSpeed.put(uuid, actual);

        return new PhysicsResult(
                0.0, 0.0,
                0.0,
                false, 0.0,
                predicted, actual
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void reseed(UUID uuid, double dy) {
        predictedVY.put(uuid, dy);
        predictionActive.put(uuid, true);
    }

    private static PhysicsResult inactiveResult(
            double predictedY, double actualY,
            double nextVY, double tolerance
    ) {
        return new PhysicsResult(
                predictedY, actualY,
                nextVY,
                false, tolerance,
                0.0, 0.0
        );
    }

    /**
     * Tolerance = BASE_TOLERANCE + (ping / 500.0) * LAG_TOLERANCE_SCALE.
     * At ping=0ms → 1.0. At ping=500ms → 1.4.
     */
    private static double toleranceFor(int ping) {
        return BASE_TOLERANCE + (ping / 500.0) * LAG_TOLERANCE_SCALE;
    }
}

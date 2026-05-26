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
 * XZ-collision prediction is a placeholder for post-0.7.0.
 */
public class PhysicsEngine {

    // -------------------------------------------------------------------------
    // Y-Prediction constants (migrated from YPredictionCheck)
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

    // -------------------------------------------------------------------------
    // Per-player predictor state
    // -------------------------------------------------------------------------

    /**
     * Simulated vertical velocity per player.
     * Equivalent to PlayerData.predictedVY — engine owns this now.
     */
    private final Map<UUID, Double>  predictedVY    = new HashMap<>();

    /**
     * Whether the predictor has been seeded for this player.
     * Equivalent to PlayerData.yPredictionActive.
     */
    private final Map<UUID, Boolean> predictionActive = new HashMap<>();

    /**
     * Grace ticks remaining after a state transition.
     * Equivalent to PlayerData.yPredictionGraceTicks.
     */
    private final Map<UUID, Integer> graceTicks = new HashMap<>();

    // -------------------------------------------------------------------------
    // Per-player horizontal predictor state (placeholder)
    // -------------------------------------------------------------------------

    private final Map<UUID, Double> prevSpeed = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs physics simulation for one player tick.
     * Call this after SnapshotBuilder.build(), before checks run.
     *
     * @param uuid     player UUID
     * @param snapshot immutable state snapshot for this tick
     * @param data     legacy PlayerData (read-only for guard flags)
     * @return PhysicsResult with all predicted vs actual values
     */
    public PhysicsResult simulate(UUID uuid, PlayerSnapshot snapshot, PlayerData data) {
        PhysicsResult yResult   = simulateY(uuid, snapshot, data);
        PhysicsResult xzResult  = simulateXZ(uuid, snapshot);

        // Merge into a single result (Y fields from yResult, XZ from xzResult)
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
        predictedVY.remove(uuid);
        predictionActive.remove(uuid);
        graceTicks.remove(uuid);
        prevSpeed.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Y simulation
    // -------------------------------------------------------------------------

    private PhysicsResult simulateY(UUID uuid, PlayerSnapshot snapshot, PlayerData data) {
        double actualY   = snapshot.y;
        double prevY     = snapshot.prevY;
        double actualDY  = snapshot.dy;
        int    ping      = snapshot.ping;
        MovementState curr = snapshot.movementState;
        MovementState prev = snapshot.prevMovementState;

        boolean active = predictionActive.getOrDefault(uuid, false);
        int     grace  = graceTicks.getOrDefault(uuid, 0);
        double  vy     = predictedVY.getOrDefault(uuid, 0.0);

        // Guards — skip / reseed, do not flag
        boolean shouldSkip = snapshot.fallFlying
                          || snapshot.passenger
                          || snapshot.health <= 0
                          || data.joinGraceTicks > 0
                          || hasLevitationOrSlowFall(data);

        if (shouldSkip) {
            reset(uuid);
            return inactiveResult(actualY, actualY, 0.0, 0.0);
        }

        // hurtTime > 0 — any damage disrupts trajectory, reseed without flagging
        if (snapshot.hurtTime > 0) {
            reseed(uuid, actualDY);
            graceTicks.put(uuid, TRANSITION_GRACE_TICKS);
            return inactiveResult(prevY + vy, actualY, vy, toleranceFor(ping));
        }

        // Ground / water / climb — reset predictor, nothing to predict
        if (curr == MovementState.GROUND
         || curr == MovementState.WATER
         || curr == MovementState.CLIMB) {
            reset(uuid);
            return inactiveResult(actualY, actualY, 0.0, 0.0);
        }

        // Transition into air from ground/water/climb — seed and grace
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

        // Decrement grace
        if (grace > 0) {
            graceTicks.put(uuid, grace - 1);
            reseed(uuid, actualDY);
            return inactiveResult(prevY + actualDY, actualY, actualDY, toleranceFor(ping));
        }

        // Active prediction — simulate one step of MC gravity
        // nextVY = (vy - 0.08) * 0.98
        double nextVY      = (vy - 0.08) * 0.98;
        double predictedY  = prevY + nextVY;
        double tolerance   = toleranceFor(ping);

        // Store updated vy for next tick
        predictedVY.put(uuid, nextVY);

        return new PhysicsResult(
                predictedY, actualY,
                nextVY,
                true, tolerance,
                0.0, 0.0
        );
    }

    // -------------------------------------------------------------------------
    // XZ simulation (placeholder — real XZ prediction post-0.7.0)
    // -------------------------------------------------------------------------

    private PhysicsResult simulateXZ(UUID uuid, PlayerSnapshot snapshot) {
        double actual = snapshot.speed;
        double predicted = prevSpeed.getOrDefault(uuid, actual);
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

    /** Seeds the predictor with the actual dy as first estimate. */
    private void reseed(UUID uuid, double dy) {
        predictedVY.put(uuid, dy);
        predictionActive.put(uuid, true);
    }

    /** Builds a PhysicsResult where predictionActive = false. */
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

    /**
     * Levitation and Slow Falling disrupt gravity — predictor must skip.
     * Reads legacy PlayerData because MobEffects require server context.
     * In Engine v2 full migration this moves into PlayerSnapshot.
     */
    private static boolean hasLevitationOrSlowFall(PlayerData data) {
        // Checked via snapshot flags in future — for now delegated to caller if needed.
        // PhysicsEngine does not import MobEffects to stay decoupled from MC internals.
        // YPredictionCheck retains its own MobEffects guard during transition period.
        return false;
    }
}

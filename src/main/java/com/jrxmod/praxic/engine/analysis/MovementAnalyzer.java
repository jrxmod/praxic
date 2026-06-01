package com.jrxmod.praxic.engine.analysis;

import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.engine.data.PlayerSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses player movement patterns each tick and produces a MovementProfile.
 * Tracks speed history, acceleration curve, strafe ratio, and jump frequency.
 * Stateful per-player — call reset() on disconnect and death.
 */
public class MovementAnalyzer {

    // Window sizes
    private static final int  SPEED_WINDOW      = 60;
    private static final long JUMP_WINDOW_MS    = 10_000L;

    // Minimum samples before a metric is considered valid
    private static final int MIN_SPEED_SAMPLES  = 20;
    private static final int MIN_STRAFE_SAMPLES = 10;
    private static final int MIN_JUMP_SAMPLES   = 3;

    // Ignore micro-movements when computing strafe ratio
    private static final double MOVING_THRESHOLD = 0.05;

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    private static class State {
        /** Horizontal speed samples, capped at SPEED_WINDOW. */
        final Deque<Double> speedHistory  = new ArrayDeque<>();

        /** Speed delta (acceleration magnitude) samples. */
        final Deque<Double> accelHistory  = new ArrayDeque<>();

        /** Strafe ratio per moving tick. */
        final Deque<Double> strafeHistory = new ArrayDeque<>();

        /** Timestamps of GROUND→JUMP transitions for jump frequency. */
        final Deque<Long>   jumpTimestamps = new ArrayDeque<>();

        /** Last recorded speed — used to compute acceleration. -1 = not seeded. */
        double lastSpeed = -1.0;

        /** Movement state last tick — used to detect jump transitions. */
        MovementState lastState = MovementState.GROUND;
    }

    private final Map<UUID, State> states = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes one tick for the given player and returns an updated profile.
     * Must be called once per tick after the snapshot is built.
     */
    public MovementProfile analyse(UUID uuid, PlayerSnapshot snap) {
        State s = states.computeIfAbsent(uuid, id -> new State());
        long now = snap.timestamp;

        // --- Speed history ---
        double speed = snap.speed;
        s.speedHistory.addLast(speed);
        if (s.speedHistory.size() > SPEED_WINDOW) s.speedHistory.pollFirst();

        // --- Acceleration (|Δspeed| per tick) ---
        if (s.lastSpeed >= 0.0) {
            double accel = Math.abs(speed - s.lastSpeed);
            s.accelHistory.addLast(accel);
            if (s.accelHistory.size() > SPEED_WINDOW) s.accelHistory.pollFirst();
        }
        s.lastSpeed = speed;

        // --- Strafe ratio (only when moving) ---
        if (speed >= MOVING_THRESHOLD) {
            double ratio = computeStrafeRatio(snap);
            s.strafeHistory.addLast(ratio);
            if (s.strafeHistory.size() > SPEED_WINDOW) s.strafeHistory.pollFirst();
        }

        // --- Jump frequency (GROUND/CLIMB → JUMP transition) ---
        boolean justJumped = snap.movementState == MovementState.JUMP
                && (s.lastState == MovementState.GROUND
                    || s.lastState == MovementState.CLIMB);
        if (justJumped) {
            s.jumpTimestamps.addLast(now);
        }
        // Expire old jump timestamps outside the window
        while (!s.jumpTimestamps.isEmpty()
                && now - s.jumpTimestamps.peekFirst() > JUMP_WINDOW_MS) {
            s.jumpTimestamps.pollFirst();
        }
        s.lastState = snap.movementState;

        // --- Build profile ---
        return buildProfile(s);
    }

    /** Clears all state for the given player. Call on disconnect and death. */
    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the strafe ratio for this tick.
     * Projects horizontal displacement onto the player's look direction.
     * Returns |lateral| / (|forward| + |lateral|), or 0.5 if no movement.
     */
    private double computeStrafeRatio(PlayerSnapshot snap) {
        double yawRad = Math.toRadians(snap.yaw);

        // Unit vector in the direction the player is looking (XZ plane)
        double fwdX =  -Math.sin(yawRad);
        double fwdZ =   Math.cos(yawRad);

        // Forward and lateral projections of the displacement vector
        double forward = snap.dx * fwdX  + snap.dz * fwdZ;
        double lateral = snap.dx * (-fwdZ) + snap.dz * fwdX;

        double absForward = Math.abs(forward);
        double absLateral = Math.abs(lateral);
        double total      = absForward + absLateral;

        return total < 1e-6 ? 0.5 : absLateral / total;
    }

    private MovementProfile buildProfile(State s) {
        int speedSamples = s.speedHistory.size();

        // Speed stats
        double avgSpeed   = -1.0;
        double speedStdDev = -1.0;
        if (speedSamples >= MIN_SPEED_SAMPLES) {
            avgSpeed    = mean(s.speedHistory);
            speedStdDev = stdDev(s.speedHistory, avgSpeed);
        }

        // Acceleration stats (one sample fewer than speed)
        double avgAccel    = -1.0;
        double accelStdDev = -1.0;
        if (s.accelHistory.size() >= MIN_SPEED_SAMPLES) {
            avgAccel    = mean(s.accelHistory);
            accelStdDev = stdDev(s.accelHistory, avgAccel);
        }

        // Strafe ratio
        double strafeRatio = -1.0;
        if (s.strafeHistory.size() >= MIN_STRAFE_SAMPLES) {
            strafeRatio = mean(s.strafeHistory);
        }

        // Jump frequency (jumps per second)
        double jumpFreq = -1.0;
        if (s.jumpTimestamps.size() >= MIN_JUMP_SAMPLES) {
            jumpFreq = s.jumpTimestamps.size() / (JUMP_WINDOW_MS / 1000.0);
        }

        return new MovementProfile(
                avgSpeed, speedStdDev,
                avgAccel, accelStdDev,
                strafeRatio,
                jumpFreq,
                speedSamples
        );
    }

    // -------------------------------------------------------------------------
    // Stats utilities
    // -------------------------------------------------------------------------

    private double mean(Deque<Double> values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private double stdDev(Deque<Double> values, double mean) {
        double variance = 0.0;
        for (double v : values) {
            double diff = v - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / values.size());
    }
}

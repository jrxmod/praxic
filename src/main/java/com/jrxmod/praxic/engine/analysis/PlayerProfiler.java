package com.jrxmod.praxic.engine.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a behavioural baseline for each player and detects deviations.
 *
 * Phase 1 (first BASELINE_TICKS ticks): collects per-metric running stats
 * using Welford's online algorithm — no large sample buffers needed.
 *
 * Phase 2 (after BASELINE_TICKS): baseline is frozen; each tick produces a
 * deviationScore showing how far current behaviour is from the baseline.
 * A sustained high score indicates toggling (cheat turned on mid-session).
 */
public class PlayerProfiler {

    /** 6000 ticks = 5 minutes at 20 TPS. */
    private static final int BASELINE_TICKS = 6000;

    /**
     * Minimum baseline samples required per core metric before baseline is
     * considered usable. Speed accumulates every tick so this is always met
     * well before BASELINE_TICKS — it guards against profiler being fed
     * mostly -1.0 values during an unusually quiet session.
     */
    private static final int MIN_SPEED_SAMPLES = 100;

    /**
     * Noise floors — minimum stddev used when normalising deviations.
     * Prevents division by near-zero when the player's baseline is very stable.
     */
    private static final double NOISE_SPEED    = 0.05;
    private static final double NOISE_ENTROPY  = 0.20;
    private static final double NOISE_CLICK    = 2.00;
    private static final double NOISE_PACKET   = 0.50;
    private static final double NOISE_CPS      = 0.50;
    private static final double NOISE_JUMP     = 0.10;

    /** deviationScore is capped at this value. */
    private static final double MAX_DEVIATION  = 10.0;

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    /**
     * Welford accumulator for one metric.
     * Tracks count, running mean, and running M2 (sum of squared deltas).
     */
    private static class Accumulator {
        int    count  = 0;
        double mean   = 0.0;
        double m2     = 0.0;

        /** Feed one valid sample. */
        void update(double value) {
            count++;
            double delta  = value - mean;
            mean         += delta / count;
            double delta2 = value - mean;
            m2           += delta * delta2;
        }

        /** Population standard deviation. Returns noise floor if count < 2. */
        double stdDev(double noiseFloor) {
            if (count < 2) return noiseFloor;
            double variance = m2 / count;
            return Math.max(Math.sqrt(variance), noiseFloor);
        }
    }

    private static class State {
        // Baseline accumulators (Welford, updated during phase 1)
        final Accumulator speed    = new Accumulator();
        final Accumulator entropy  = new Accumulator();
        final Accumulator click    = new Accumulator();
        final Accumulator packet   = new Accumulator();
        final Accumulator cps      = new Accumulator();
        final Accumulator jump     = new Accumulator();

        /** Ticks elapsed (counts up to BASELINE_TICKS). */
        int ticksCollected = 0;

        /** Set to true once baseline window is complete and core metrics are valid. */
        boolean baselineReady = false;
    }

    private final Map<UUID, State> states = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes one tick and returns the player's baseline result.
     * Must be called after MovementProfile, RotationProfile, TimingProfile
     * are already produced for this tick.
     *
     * @param uuid  player UUID
     * @param mov   movement profile for this tick
     * @param rot   rotation profile for this tick
     * @param tim   timing profile for this tick
     */
    public PlayerBaseline analyse(
            UUID uuid,
            MovementProfile mov,
            RotationProfile rot,
            TimingProfile   tim
    ) {
        State s = states.computeIfAbsent(uuid, id -> new State());

        if (!s.baselineReady) {
            // ---- Phase 1: collect baseline samples ----
            feedAccumulators(s, mov, rot, tim);
            s.ticksCollected = Math.min(s.ticksCollected + 1, BASELINE_TICKS);

            boolean windowComplete = s.ticksCollected >= BASELINE_TICKS;
            boolean coreValid      = s.speed.count >= MIN_SPEED_SAMPLES;

            if (windowComplete && coreValid) {
                s.baselineReady = true;
            }

            return new PlayerBaseline(
                    false, s.ticksCollected, BASELINE_TICKS,
                    -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0
            );
        }

        // ---- Phase 2: compute deviations ----
        double speedDev   = deviation(mov.avgSpeed,              s.speed,   NOISE_SPEED);
        double entropyDev = deviation(rot.entropy,               s.entropy, NOISE_ENTROPY);
        double clickDev   = deviation(tim.clickIntervalStdDev,   s.click,   NOISE_CLICK);
        double packetDev  = deviation(tim.packetIntervalStdDev,  s.packet,  NOISE_PACKET);
        double cpsDev     = deviation(tim.avgCps,                s.cps,     NOISE_CPS);
        double jumpDev    = deviation(mov.jumpFrequency,         s.jump,    NOISE_JUMP);

        double overall = overallDeviation(speedDev, entropyDev, clickDev,
                                          packetDev, cpsDev, jumpDev);

        return new PlayerBaseline(
                true, BASELINE_TICKS, BASELINE_TICKS,
                overall,
                speedDev, entropyDev, clickDev, packetDev, cpsDev, jumpDev
        );
    }

    /** Removes all state for a player on disconnect. */
    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Feed valid (non -1.0) samples into each accumulator. */
    private void feedAccumulators(State s,
                                  MovementProfile mov,
                                  RotationProfile rot,
                                  TimingProfile   tim) {
        if (mov.avgSpeed          >= 0.0) s.speed.update(mov.avgSpeed);
        if (rot.entropy           >= 0.0) s.entropy.update(rot.entropy);
        if (tim.clickIntervalStdDev  >= 0.0) s.click.update(tim.clickIntervalStdDev);
        if (tim.packetIntervalStdDev >= 0.0) s.packet.update(tim.packetIntervalStdDev);
        if (tim.avgCps            >= 0.0) s.cps.update(tim.avgCps);
        if (mov.jumpFrequency     >= 0.0) s.jump.update(mov.jumpFrequency);
    }

    /**
     * Computes z-score deviation of a current value from a baseline accumulator.
     * Returns -1.0 if current value is invalid (-1.0) or baseline has < 2 samples.
     */
    private double deviation(double current, Accumulator acc, double noiseFloor) {
        if (current < 0.0)    return -1.0;
        if (acc.count < 2)    return -1.0;
        double sd = acc.stdDev(noiseFloor);
        return Math.min(Math.abs(current - acc.mean) / sd, MAX_DEVIATION);
    }

    /**
     * Averages all valid (non -1.0) per-metric deviations.
     * Returns -1.0 if no metric is valid.
     */
    private double overallDeviation(double... deviations) {
        double sum   = 0.0;
        int    count = 0;
        for (double d : deviations) {
            if (d >= 0.0) {
                sum += d;
                count++;
            }
        }
        return count == 0 ? -1.0 : sum / count;
    }
}

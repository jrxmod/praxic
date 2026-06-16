package com.jrxmod.praxic.engine.decision;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maintains a per-player confidence score representing the probability
 * that the player is cheating, based on weighted check flags.
 *
 * Replaces the flat per-check VL system as the primary action gate.
 * Legacy PlayerData.violations is kept for the REVEX event payload.
 *
 * Score range: 0.0 (clean) to 1.0 (certain cheat).
 *
 * Weights: how much evidence each check provides per flag.
 * Correlation: multiple distinct checks firing in a short window multiply the weight.
 * Decay: after DECAY_GRACE_MS of silence, score * DECAY_FACTOR every tick.
 */
public class ConfidenceEngine {

    // -------------------------------------------------------------------------
    // Per-check evidence weights
    // -------------------------------------------------------------------------

    private static final Map<String, Double> WEIGHTS = new HashMap<>();
    static {
        // Movement — strong evidence of bypass mods
        WEIGHTS.put("FlyCheck",         0.25);
        WEIGHTS.put("YPredictionCheck", 0.25);
        WEIGHTS.put("SpeedCheck",       0.15);
        WEIGHTS.put("BoatFlyCheck",     0.20);
        WEIGHTS.put("JesusCheck",       0.15);
        WEIGHTS.put("NoFallCheck",      0.15);
        WEIGHTS.put("VelocityCheck",    0.15);
        WEIGHTS.put("SprintCheck",      0.10);
        // Combat — moderate to high evidence
        WEIGHTS.put("KillAuraCheck",    0.25);
        WEIGHTS.put("ReachCheck",       0.20);
        WEIGHTS.put("RotationCheck",    0.20);
        // World — moderate evidence (easier to false-positive)
        WEIGHTS.put("ScaffoldCheck",    0.15);
        WEIGHTS.put("FastBreakCheck",   0.15);
        // Client automation — high precision checks
        WEIGHTS.put("AutoClickerCheck", 0.25);
        WEIGHTS.put("TimerCheck",       0.25);
        WEIGHTS.put("AutoTotemCheck",   0.15);
        WEIGHTS.put("InventoryCheck",   0.10);
    }

    /** Fallback weight for any check not listed above. */
    private static final double DEFAULT_WEIGHT = 0.10;

    /** Score is clamped to this ceiling. */
    private static final double MAX_SCORE = 1.0;

    // -------------------------------------------------------------------------
    // Correlation multipliers
    // -------------------------------------------------------------------------

    /** 2 distinct checks within this window → CORRELATION_2_MULT. */
    private static final long   CORRELATION_2_WINDOW_MS = 3_000L;
    private static final double CORRELATION_2_MULT      = 1.5;

    /** 3+ distinct checks within this window → CORRELATION_3_MULT. */
    private static final long   CORRELATION_3_WINDOW_MS = 5_000L;
    private static final double CORRELATION_3_MULT      = 2.0;

    // -------------------------------------------------------------------------
    // Decay parameters
    // -------------------------------------------------------------------------

    /** Grace period of no flags before decay activates. */
    private static final long   DECAY_GRACE_MS = 10_000L;

    /**
     * Multiplicative decay applied each tick once grace period has elapsed.
     * At 20 TPS: score × 0.95 per tick = score × ~0.36 per second.
     * A score of 0.80 decays below 0.30 in roughly 1 second after grace.
     */
    private static final double DECAY_FACTOR = 0.95;

    // -------------------------------------------------------------------------
    // Anomaly nudge — soft boost from baseline deviations (AnomalyScoreEngine)
    // -------------------------------------------------------------------------

    /** Minimum anomaly score before it contributes to confidence. */
    private static final double ANOMALY_NUDGE_THRESHOLD = 0.70;

    /** Weight applied when nudging confidence from anomaly. */
    private static final double ANOMALY_NUDGE_WEIGHT = 0.03;

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    private static class FlagEntry {
        final long   timestamp;
        final String checkName;
        FlagEntry(long ts, String name) { timestamp = ts; checkName = name; }
    }

    private static class State {
        double score        = 0.0;
        long   lastFlagTime = -1L;

        /** Recent flag entries for correlation window (max CORRELATION_3_WINDOW_MS). */
        final Deque<FlagEntry> recentFlags = new ArrayDeque<>();
    }

    private final Map<UUID, State> states = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Records a flag from a check and updates the player's confidence score.
     * Applies correlation multiplier if multiple distinct checks fired recently.
     *
     * @param uuid      player UUID
     * @param checkName check that fired
     */
    public void flag(UUID uuid, String checkName) {
        State s = states.computeIfAbsent(uuid, id -> new State());
        long now = System.currentTimeMillis();

        double weight      = WEIGHTS.getOrDefault(checkName, DEFAULT_WEIGHT);
        double multiplier  = correlationMultiplier(s, checkName, now);

        s.score        = Math.min(s.score + weight * multiplier, MAX_SCORE);
        s.lastFlagTime = now;

        // Track for future correlation
        s.recentFlags.addLast(new FlagEntry(now, checkName));
        pruneRecentFlags(s, now);
    }

    /**
     * Nudges confidence upward from a high anomaly score.
     * Called by CheckManager when AnomalyScoreEngine reports sustained deviation.
     *
     * @param uuid         player UUID
     * @param anomalyScore current score from AnomalyScoreEngine (0.0–1.0)
     */
    public void nudgeFromAnomaly(UUID uuid, double anomalyScore) {
        if (anomalyScore < ANOMALY_NUDGE_THRESHOLD) return;
        State s = states.computeIfAbsent(uuid, id -> new State());
        double nudge = (anomalyScore - ANOMALY_NUDGE_THRESHOLD) * ANOMALY_NUDGE_WEIGHT;
        s.score = Math.min(s.score + nudge, MAX_SCORE);
    }

    /**
     * Applies time-based decay. Call once per tick per active player.
     * Decay activates only after DECAY_GRACE_MS of no flags.
     *
     * @param uuid  player UUID
     * @param nowMs current wall time in milliseconds
     */
    public void tickDecay(UUID uuid, long nowMs) {
        State s = states.get(uuid);
        if (s == null || s.score <= 0.0 || s.lastFlagTime < 0) return;
        if (nowMs - s.lastFlagTime >= DECAY_GRACE_MS) {
            s.score = Math.max(s.score * DECAY_FACTOR, 0.0);
        }
    }

    /**
     * Returns the current confidence score for a player.
     * Returns 0.0 if the player has no recorded state.
     */
    public double getScore(UUID uuid) {
        State s = states.get(uuid);
        return s != null ? s.score : 0.0;
    }

    /** Clears all state for a player. Call on disconnect. */
    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the correlation multiplier for a flag.
     * Counts distinct check names in each correlation window (including current flag).
     */
    private double correlationMultiplier(State s, String checkName, long now) {
        pruneRecentFlags(s, now);

        Set<String> in3s = new HashSet<>();
        Set<String> in5s = new HashSet<>();

        for (FlagEntry e : s.recentFlags) {
            long age = now - e.timestamp;
            if (age <= CORRELATION_2_WINDOW_MS) in3s.add(e.checkName);
            if (age <= CORRELATION_3_WINDOW_MS) in5s.add(e.checkName);
        }

        in3s.add(checkName);
        in5s.add(checkName);

        if (in5s.size() >= 3) return CORRELATION_3_MULT;
        if (in3s.size() >= 2) return CORRELATION_2_MULT;
        return 1.0;
    }

    /** Removes entries older than the widest correlation window. */
    private void pruneRecentFlags(State s, long now) {
        while (!s.recentFlags.isEmpty()
                && now - s.recentFlags.peekFirst().timestamp > CORRELATION_3_WINDOW_MS) {
            s.recentFlags.pollFirst();
        }
    }
}

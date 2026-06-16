package com.jrxmod.praxic.engine.decision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Accumulates sub-threshold behavioural anomalies from PlayerBaseline.deviationScore.
 *
 * Where ConfidenceEngine reacts to explicit check flags (hard evidence),
 * AnomalyScoreEngine reacts to subtle but sustained deviation from a player's
 * own baseline — the signature of closet-cheaters who keep values just below
 * per-check detection thresholds.
 *
 * When anomaly score exceeds NUDGE_THRESHOLD, CheckManager calls
 * ConfidenceEngine.nudgeFromAnomaly() to apply a soft confidence boost.
 *
 * Score range: 0.0 (no anomaly) to 1.0 (maximum sustained deviation).
 */
public class AnomalyScoreEngine {

    // -------------------------------------------------------------------------
    // Parameters
    // -------------------------------------------------------------------------

    /**
     * Minimum deviationScore before any anomaly accumulation begins.
     * Baseline deviationScore ~1.0 is normal variation; 2.0+ is suspicious.
     */
    private static final double SUSPICION_THRESHOLD = 2.0;

    /**
     * Score added per tick when deviationScore exceeds SUSPICION_THRESHOLD.
     * At 20 TPS, a sustained deviation reaching 3.0 takes ~33 seconds to
     * push anomaly from 0.0 to 0.66 (below ConfidenceEngine nudge threshold).
     * This is intentionally slow — we want sustained deviation, not spikes.
     */
    private static final double ACCUMULATION_RATE = 0.001;

    /**
     * Multiplicative decay applied every tick regardless of flags.
     * Anomaly decays much faster than confidence — it reflects current behaviour,
     * not historical evidence. A player who stops deviating should clear quickly.
     * At 20 TPS: score × 0.999 per tick = score × ~0.82 per second.
     */
    private static final double DECAY_FACTOR = 0.999;

    /** Score ceiling. */
    private static final double MAX_SCORE = 1.0;

    /**
     * Threshold above which CheckManager triggers a confidence nudge.
     * Matches ConfidenceEngine.ANOMALY_NUDGE_THRESHOLD.
     */
    public static final double NUDGE_THRESHOLD = 0.70;

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    private final Map<UUID, Double> scores = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feeds one tick of deviation data and updates the anomaly score.
     * Call once per tick per player after PlayerBaseline is produced.
     *
     * @param uuid           player UUID
     * @param deviationScore PlayerBaseline.deviationScore for this tick.
     *                       -1.0 means baseline not ready — no accumulation, still decay.
     */
    public void feed(UUID uuid, double deviationScore) {
        double current = scores.getOrDefault(uuid, 0.0);

        // Always decay — anomaly reflects current state, not history
        current *= DECAY_FACTOR;

        // Accumulate only when baseline is ready and deviation is suspicious
        if (deviationScore >= SUSPICION_THRESHOLD) {
            double excess = deviationScore - SUSPICION_THRESHOLD;
            current += ACCUMULATION_RATE * (1.0 + excess);
        }

        scores.put(uuid, Math.min(Math.max(current, 0.0), MAX_SCORE));
    }

    /**
     * Returns the current anomaly score for a player.
     * Returns 0.0 if the player has no recorded state.
     */
    public double getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0.0);
    }

    /** Clears all state for a player. Call on disconnect. */
    public void reset(UUID uuid) {
        scores.remove(uuid);
    }
}

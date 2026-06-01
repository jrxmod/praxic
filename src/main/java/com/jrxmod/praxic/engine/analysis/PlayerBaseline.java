package com.jrxmod.praxic.engine.analysis;

/**
 * Immutable result of PlayerProfiler for a single tick.
 * Represents how much the player's current behaviour deviates from their baseline.
 * All deviation fields are -1.0 if insufficient data for that metric.
 */
public final class PlayerBaseline {

    // -------------------------------------------------------------------------
    // Baseline readiness
    // -------------------------------------------------------------------------

    /** True once the baseline collection window has completed. */
    public final boolean baselineReady;

    /** Ticks collected so far during baseline phase. Capped at BASELINE_TICKS. */
    public final int baselineTicksCollected;

    /** Total ticks required before baseline is considered ready. */
    public final int baselineTicksRequired;

    // -------------------------------------------------------------------------
    // Overall deviation
    // -------------------------------------------------------------------------

    /**
     * Weighted average of all valid per-metric deviations from baseline.
     * 0.0 = behaviour matches baseline exactly.
     * ~1.0 = one standard-deviation shift (normal variation).
     * 3.0+ = suspicious — possible toggling or cheat activation.
     * -1.0 if baseline not ready.
     */
    public final double deviationScore;

    // -------------------------------------------------------------------------
    // Per-metric deviations (z-score: |current - mean| / stddev)
    // -1.0 if either baseline or current value is invalid for that metric.
    // -------------------------------------------------------------------------

    /** Deviation of current avgSpeed from baseline. */
    public final double speedDeviation;

    /** Deviation of current rotation entropy from baseline. */
    public final double entropyDeviation;

    /** Deviation of current clickIntervalStdDev from baseline. */
    public final double clickDeviation;

    /** Deviation of current packetIntervalStdDev from baseline. */
    public final double packetDeviation;

    /** Deviation of current avgCps from baseline. */
    public final double cpsDeviation;

    /** Deviation of current jumpFrequency from baseline. */
    public final double jumpDeviation;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PlayerBaseline(
            boolean baselineReady,
            int baselineTicksCollected,
            int baselineTicksRequired,
            double deviationScore,
            double speedDeviation,
            double entropyDeviation,
            double clickDeviation,
            double packetDeviation,
            double cpsDeviation,
            double jumpDeviation
    ) {
        this.baselineReady          = baselineReady;
        this.baselineTicksCollected = baselineTicksCollected;
        this.baselineTicksRequired  = baselineTicksRequired;
        this.deviationScore         = deviationScore;
        this.speedDeviation         = speedDeviation;
        this.entropyDeviation       = entropyDeviation;
        this.clickDeviation         = clickDeviation;
        this.packetDeviation        = packetDeviation;
        this.cpsDeviation           = cpsDeviation;
        this.jumpDeviation          = jumpDeviation;
    }
}

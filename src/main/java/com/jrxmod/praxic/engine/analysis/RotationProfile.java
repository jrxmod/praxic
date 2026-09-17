package com.jrxmod.praxic.engine.analysis;

/**
 * Immutable result of RotationAnalyzer for a single tick.
 * Consumed by rotation-based detection checks.
 */
public final class RotationProfile {

    /**
     * Shannon entropy of deltaYaw values over the last 40 ticks.
     * Human: ~3.5-4.0 bits. AimBot: ~1.0-2.0 bits.
     * -1.0 if not enough data yet (fewer than 40 ticks collected).
     */
    public final double entropy;

    /** Max snap angle observed in the last 20 ticks (absolute deltaYaw). */
    public final double maxSnapAngle;

    /** Average rotation speed (sqrt(deltaYaw^2 + deltaPitch^2)) over the window. */
    public final double avgRotationSpeed;

    /**
     * Coefficient of variation of per-tick rotation speed over the window
     * (stddev / mean). Aim-assist modules that rotate with a constant per-tick
     * step (e.g. slowlyTurnTowards) produce values close to 0; hand mouse
     * input is bursty and produces values above ~0.3.
     * -1.0 if the window is too small or the mean speed is ~0.
     */
    public final double rotationSpeedCV;

    /** Ticks in the window with a >320deg yaw delta (modulo-360 wrap artifact). */
    public final int largeSnapCount;

    /**
     * Snap angle in the first 3 ticks after a kill event.
     * Human: 10-30deg. Kill Aura: 90deg+.
     * -1.0 if no recent kill event.
     */
    public final double postKillSnapAngle;

    /** Number of deltaYaw samples collected so far (max 40). */
    public final int sampleCount;

    public RotationProfile(
            double entropy,
            double maxSnapAngle,
            double avgRotationSpeed,
            double rotationSpeedCV,
            int largeSnapCount,
            double postKillSnapAngle,
            int sampleCount
    ) {
        this.entropy           = entropy;
        this.maxSnapAngle      = maxSnapAngle;
        this.avgRotationSpeed  = avgRotationSpeed;
        this.rotationSpeedCV   = rotationSpeedCV;
        this.largeSnapCount    = largeSnapCount;
        this.postKillSnapAngle = postKillSnapAngle;
        this.sampleCount       = sampleCount;
    }
}

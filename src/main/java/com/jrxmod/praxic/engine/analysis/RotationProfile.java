package com.jrxmod.praxic.engine.analysis;

/**
 * Immutable result of RotationAnalyzer for a single tick.
 * Consumed by rotation-based detection checks.
 */
public final class RotationProfile {

    /**
     * Shannon entropy of deltaYaw values over the last 40 ticks.
     * Human: ~3.5–4.0 bits. AimBot: ~1.0–2.0 bits.
     * -1.0 if not enough data yet (fewer than 40 ticks collected).
     */
    public final double entropy;

    /** Max snap angle observed in the last 20 ticks (absolute deltaYaw). */
    public final double maxSnapAngle;

    /** Average rotation speed (sqrt(deltaYaw² + deltaPitch²)) over the window. */
    public final double avgRotationSpeed;

    /**
     * Snap angle in the first 3 ticks after a kill event.
     * Human: 10–30°. Kill Aura: 90°+.
     * -1.0 if no recent kill event.
     */
    public final double postKillSnapAngle;

    /** Number of deltaYaw samples collected so far (max 40). */
    public final int sampleCount;

    public RotationProfile(
            double entropy,
            double maxSnapAngle,
            double avgRotationSpeed,
            double postKillSnapAngle,
            int sampleCount
    ) {
        this.entropy           = entropy;
        this.maxSnapAngle      = maxSnapAngle;
        this.avgRotationSpeed  = avgRotationSpeed;
        this.postKillSnapAngle = postKillSnapAngle;
        this.sampleCount       = sampleCount;
    }
}

package com.jrxmod.praxic.engine.analysis;

/**
 * Aggregates all per-tick analysis results for one player into a single object.
 * Produced by CheckManager after all four analysis steps complete.
 * Checks that need multiple profiles read from here instead of four separate maps.
 * All fields are non-null — if an analyser has no data yet its profile carries -1.0 values.
 */
public final class PlayerAnalytics {

    /** Rotation entropy, snap angles, post-kill snap. */
    public final RotationProfile rotation;

    /** Click interval stddev, packet interval stddev, avgCps. */
    public final TimingProfile timing;

    /** Speed stats, acceleration curve, strafe ratio, jump frequency. */
    public final MovementProfile movement;

    /** Baseline readiness flag, overall deviation score, per-metric deviations. */
    public final PlayerBaseline baseline;

    public PlayerAnalytics(
            RotationProfile rotation,
            TimingProfile   timing,
            MovementProfile movement,
            PlayerBaseline  baseline
    ) {
        this.rotation = rotation;
        this.timing   = timing;
        this.movement = movement;
        this.baseline = baseline;
    }
}

package com.jrxmod.praxic.engine.analysis;

/**
 * Immutable result of movement analysis for a single tick.
 * All fields are averages/stats over their respective windows.
 * -1.0 means insufficient samples — checks must guard before using.
 */
public final class MovementProfile {

    /**
     * Average horizontal speed (XZ) over last 60 ticks.
     * -1.0 if fewer than 20 samples.
     */
    public final double avgSpeed;

    /**
     * Standard deviation of horizontal speed over last 60 ticks.
     * Low stdDev + high avgSpeed = SpeedHack signature.
     * -1.0 if fewer than 20 samples.
     */
    public final double speedStdDev;

    /**
     * Average acceleration magnitude (second derivative of speed) over last 60 ticks.
     * -1.0 if fewer than 20 samples.
     */
    public final double avgAcceleration;

    /**
     * Standard deviation of acceleration over last 60 ticks.
     * Near-zero stdDev = bot-like constant acceleration.
     * -1.0 if fewer than 20 samples.
     */
    public final double accelerationStdDev;

    /**
     * Ratio of lateral movement to total horizontal movement (0.0 - 1.0).
     * Computed only on ticks where player is actually moving.
     * 1.0 = pure strafe, 0.0 = pure forward/back.
     * -1.0 if fewer than 10 moving-tick samples.
     */
    public final double strafeRatio;

    /**
     * Jumps per second over the last 10 seconds.
     * -1.0 if fewer than 3 jumps recorded.
     */
    public final double jumpFrequency;

    /** Number of speed samples used — informational. */
    public final int sampleCount;

    public MovementProfile(
            double avgSpeed,
            double speedStdDev,
            double avgAcceleration,
            double accelerationStdDev,
            double strafeRatio,
            double jumpFrequency,
            int sampleCount
    ) {
        this.avgSpeed             = avgSpeed;
        this.speedStdDev          = speedStdDev;
        this.avgAcceleration      = avgAcceleration;
        this.accelerationStdDev   = accelerationStdDev;
        this.strafeRatio          = strafeRatio;
        this.jumpFrequency        = jumpFrequency;
        this.sampleCount          = sampleCount;
    }
}

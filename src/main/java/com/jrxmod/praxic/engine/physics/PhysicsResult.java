package com.jrxmod.praxic.engine.physics;

/**
 * Immutable result of PhysicsEngine simulation for a single tick.
 * Produced by PhysicsEngine, consumed by detection checks.
 */
public final class PhysicsResult {

    // -------------------------------------------------------------------------
    // Vertical prediction
    // -------------------------------------------------------------------------

    /** Expected Y this tick, computed from last tick's simulated vy. */
    public final double predictedY;

    /** Actual Y reported by the player. */
    public final double actualY;

    /**
     * Vertical delta: actualY - predictedY.
     * Positive = player is ABOVE prediction (suspicious — possible fly/hover).
     * Negative = player is below prediction (legitimate — collisions, slabs, stairs).
     */
    public final double yDelta;

    /** Simulated vertical velocity that will be used as seed next tick. */
    public final double nextPredictedVY;

    /**
     * True if the predictor is active and yDelta is meaningful.
     * False during grace ticks (transition, first seed, hurtTime reset).
     */
    public final boolean predictionActive;

    /**
     * Total tolerance for this tick: base tolerance + lag compensation.
     * Flag only if yDelta > yTolerance.
     */
    public final double yTolerance;

    // -------------------------------------------------------------------------
    // Horizontal prediction (placeholder — XZ physics added post-0.7.0)
    // -------------------------------------------------------------------------

    /** Predicted horizontal speed this tick based on last tick's actual speed. */
    public final double predictedSpeed;

    /** Actual horizontal speed (XZ magnitude). */
    public final double actualSpeed;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PhysicsResult(
            double predictedY,   double actualY,
            double nextPredictedVY,
            boolean predictionActive, double yTolerance,
            double predictedSpeed, double actualSpeed
    ) {
        this.predictedY        = predictedY;
        this.actualY           = actualY;
        this.yDelta            = actualY - predictedY;
        this.nextPredictedVY   = nextPredictedVY;
        this.predictionActive  = predictionActive;
        this.yTolerance        = yTolerance;
        this.predictedSpeed    = predictedSpeed;
        this.actualSpeed       = actualSpeed;
    }
}

package com.jrxmod.praxic.engine.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the derived yDelta field of PhysicsResult.
 */
class PhysicsResultTest {

    @Test
    void yDeltaIsActualMinusPredicted() {
        PhysicsResult result = new PhysicsResult(
                100.0, 101.5, // predictedY, actualY
                0.4,          // nextPredictedVY
                true, 1.0,    // predictionActive, yTolerance
                0.0, 0.0      // predictedSpeed, actualSpeed
        );
        assertEquals(1.5, result.yDelta, 1e-9);
    }

    @Test
    void belowPredictionIsNegative() {
        PhysicsResult result = new PhysicsResult(
                101.5, 100.0,
                0.4,
                true, 1.0,
                0.0, 0.0
        );
        assertEquals(-1.5, result.yDelta, 1e-9);
    }
}

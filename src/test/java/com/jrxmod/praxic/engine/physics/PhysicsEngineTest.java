package com.jrxmod.praxic.engine.physics;

import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.data.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Y-prediction behaviour in PhysicsEngine: transition seeding,
 * grace tick handling, and the vanilla gravity step.
 */
class PhysicsEngineTest {

    private static final double DELTA = 1e-6;

    private static PlayerSnapshot snapshot(
            MovementState prev, MovementState curr,
            double y, double prevY, double dy, int ping) {
        return new PlayerSnapshot(
                0L,
                0.0, y, 0.0,        // x, y, z
                0.0, prevY, 0.0,    // prevX, prevY, prevZ
                0f, 0f, 0f, 0f,     // yaw, pitch, prevYaw, prevPitch
                false, false, false, // onGround, inWater, onClimbable
                false, false,       // fallFlying, passenger
                curr, prev,         // movementState, prevMovementState
                0, 20f,             // hurtTime, health
                ping
        );
    }

    @Test
    void groundStateReturnsInactive() {
        PhysicsEngine engine = new PhysicsEngine();
        UUID uuid = UUID.randomUUID();
        PlayerData data = new PlayerData(0.0, 100.0, 0.0);
        data.joinGraceTicks = 0;

        PhysicsResult result = engine.simulate(uuid,
                snapshot(MovementState.GROUND, MovementState.GROUND, 100.0, 100.0, 0.0, 0),
                data);

        assertFalse(result.predictionActive);
        assertEquals(100.0, result.predictedY, DELTA);
    }

    @Test
    void gravityStepUsesVanillaFormula() {
        PhysicsEngine engine = new PhysicsEngine();
        UUID uuid = UUID.randomUUID();
        PlayerData data = new PlayerData(0.0, 100.0, 0.0);
        data.joinGraceTicks = 0;

        // Ground -> jump transition seeds vy with the actual dy (0.5) and
        // starts a 10-tick transition grace.
        engine.simulate(uuid,
                snapshot(MovementState.GROUND, MovementState.JUMP, 100.5, 100.0, 0.5, 0),
                data);

        // Burn 10 grace ticks; each reseeds vy from the constant dy.
        for (int i = 0; i < 10; i++) {
            engine.simulate(uuid,
                    snapshot(MovementState.AIR, MovementState.AIR, 100.5, 100.0, 0.5, 0),
                    data);
        }

        // First active tick: nextVY = (0.5 - 0.08) * 0.98.
        PhysicsResult result = engine.simulate(uuid,
                snapshot(MovementState.AIR, MovementState.AIR, 100.4, 100.0, 0.4, 0),
                data);

        double expectedVy = (0.5 - 0.08) * 0.98;
        assertTrue(result.predictionActive);
        assertEquals(expectedVy, result.nextPredictedVY, DELTA);
        assertEquals(100.0 + expectedVy, result.predictedY, DELTA);
    }

    @Test
    void toleranceScalesWithPing() {
        PhysicsEngine engine = new PhysicsEngine();
        UUID uuid = UUID.randomUUID();
        PlayerData data = new PlayerData(0.0, 100.0, 0.0);
        data.joinGraceTicks = 0;

        PhysicsResult atZero = engine.simulate(uuid,
                snapshot(MovementState.GROUND, MovementState.JUMP, 100.5, 100.0, 0.5, 0),
                data);
        engine.reset(uuid);

        PhysicsResult atCap = engine.simulate(uuid,
                snapshot(MovementState.GROUND, MovementState.JUMP, 100.5, 100.0, 0.5, 500),
                data);

        assertEquals(1.0, atZero.yTolerance, DELTA);
        assertEquals(1.4, atCap.yTolerance, DELTA);
    }
}

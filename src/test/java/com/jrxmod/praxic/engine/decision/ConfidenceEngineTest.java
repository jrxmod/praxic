package com.jrxmod.praxic.engine.decision;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies weighted flag accumulation, correlation multipliers, decay and
 * clamping in ConfidenceEngine.
 */
class ConfidenceEngineTest {

    private static final double DELTA = 1e-9;

    @Test
    void freshPlayerHasZeroScore() {
        ConfidenceEngine engine = new ConfidenceEngine();
        assertEquals(0.0, engine.getScore(UUID.randomUUID()), DELTA);
    }

    @Test
    void knownCheckUsesConfiguredWeight() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "FlyCheck");
        assertEquals(0.25, engine.getScore(uuid), DELTA);
    }

    @Test
    void unknownCheckUsesDefaultWeight() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "UnknownCheck");
        assertEquals(0.10, engine.getScore(uuid), DELTA);
    }

    @Test
    void scoreIsClampedToOne() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "GhostTrapCheck"); // weight 0.85
        engine.flag(uuid, "GhostTrapCheck");
        assertEquals(1.0, engine.getScore(uuid), DELTA);
    }

    @Test
    void twoDistinctChecksApplyCorrelationMultiplier() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "FlyCheck");   // 0.25
        engine.flag(uuid, "SpeedCheck"); // 0.15 * 1.5 = 0.225
        assertEquals(0.475, engine.getScore(uuid), DELTA);
    }

    @Test
    void decayAfterGraceReducesScore() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "FlyCheck"); // 0.25
        engine.tickDecay(uuid, System.currentTimeMillis() + 11_000L);
        assertEquals(0.25 * 0.95, engine.getScore(uuid), DELTA);
    }

    @Test
    void resetClearsScore() {
        ConfidenceEngine engine = new ConfidenceEngine();
        UUID uuid = UUID.randomUUID();
        engine.flag(uuid, "FlyCheck");
        engine.reset(uuid);
        assertEquals(0.0, engine.getScore(uuid), DELTA);
    }
}

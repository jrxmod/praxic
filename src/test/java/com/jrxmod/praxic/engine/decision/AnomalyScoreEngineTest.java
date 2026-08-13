package com.jrxmod.praxic.engine.decision;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies accumulation, decay, clamping and reset in AnomalyScoreEngine.
 */
class AnomalyScoreEngineTest {

    @Test
    void freshPlayerHasZeroScore() {
        AnomalyScoreEngine engine = new AnomalyScoreEngine();
        assertEquals(0.0, engine.getScore(UUID.randomUUID()), 1e-9);
    }

    @Test
    void belowThresholdDoesNotAccumulate() {
        AnomalyScoreEngine engine = new AnomalyScoreEngine();
        UUID uuid = UUID.randomUUID();
        engine.feed(uuid, 1.0); // below SUSPICION_THRESHOLD (2.0)
        assertEquals(0.0, engine.getScore(uuid), 1e-9);
    }

    @Test
    void suspiciousDeviationAccumulates() {
        AnomalyScoreEngine engine = new AnomalyScoreEngine();
        UUID uuid = UUID.randomUUID();
        // excess = 3.0 - 2.0 = 1.0; delta = 0.001 * (1 + 1.0) = 0.002
        engine.feed(uuid, 3.0);
        assertEquals(0.002, engine.getScore(uuid), 1e-9);
    }

    @Test
    void scoreIsClampedToOne() {
        AnomalyScoreEngine engine = new AnomalyScoreEngine();
        UUID uuid = UUID.randomUUID();
        for (int i = 0; i < 100_000; i++) {
            engine.feed(uuid, 3.0);
        }
        assertEquals(1.0, engine.getScore(uuid), 1e-9);
    }

    @Test
    void resetClearsScore() {
        AnomalyScoreEngine engine = new AnomalyScoreEngine();
        UUID uuid = UUID.randomUUID();
        engine.feed(uuid, 3.0);
        engine.reset(uuid);
        assertEquals(0.0, engine.getScore(uuid), 1e-9);
    }
}

package com.jrxmod.praxic.engine.physics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tick countdown, kind isolation and reset of ImpulseEngine.
 */
class ImpulseEngineTest {

    @Test
    void freshPlayerHasNoImpulse() {
        ImpulseEngine engine = new ImpulseEngine();
        assertFalse(engine.isActive(UUID.randomUUID()));
        assertEquals(0, engine.remaining(UUID.randomUUID()));
    }

    @Test
    void recordUsesDefaultDuration() {
        ImpulseEngine engine = new ImpulseEngine();
        UUID uuid = UUID.randomUUID();
        engine.record(uuid, ImpulseEngine.Kind.WIND);
        assertTrue(engine.isActive(uuid));
        assertTrue(engine.isActive(uuid, ImpulseEngine.Kind.WIND));
        assertFalse(engine.isActive(uuid, ImpulseEngine.Kind.RIPTIDE));
        assertEquals(ImpulseEngine.Kind.WIND.defaultTicks, engine.remaining(uuid));
    }

    @Test
    void tickDecrementsAndExpires() {
        ImpulseEngine engine = new ImpulseEngine();
        UUID uuid = UUID.randomUUID();
        engine.record(uuid, ImpulseEngine.Kind.KNOCKBACK, 3);
        engine.tick(uuid);
        engine.tick(uuid);
        assertTrue(engine.isActive(uuid));
        assertEquals(1, engine.remaining(uuid, ImpulseEngine.Kind.KNOCKBACK));
        engine.tick(uuid);
        assertFalse(engine.isActive(uuid));
        assertEquals(0, engine.remaining(uuid));
    }

    @Test
    void laterRecordExtendsWindow() {
        ImpulseEngine engine = new ImpulseEngine();
        UUID uuid = UUID.randomUUID();
        engine.record(uuid, ImpulseEngine.Kind.EXPLOSION, 2);
        engine.record(uuid, ImpulseEngine.Kind.EXPLOSION, 8);
        assertEquals(8, engine.remaining(uuid, ImpulseEngine.Kind.EXPLOSION));
    }

    @Test
    void resetClearsState() {
        ImpulseEngine engine = new ImpulseEngine();
        UUID uuid = UUID.randomUUID();
        engine.record(uuid, ImpulseEngine.Kind.MACE);
        engine.reset(uuid);
        assertFalse(engine.isActive(uuid));
    }
}

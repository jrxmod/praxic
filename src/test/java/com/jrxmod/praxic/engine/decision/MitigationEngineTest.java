package com.jrxmod.praxic.engine.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure predicates for packet mitigation. Falling packets must never be
 * rejected as hover, and teleport grace must never rubberband a pearl.
 */
class MitigationEngineTest {

    @Test
    void hoverRejectsSustainedAirWithoutFall() {
        assertTrue(MitigationEngine.rejectHover(90, 80, 0.0));
        assertTrue(MitigationEngine.rejectHover(90, 80, 0.2));
    }

    @Test
    void hoverAllowsNaturalFall() {
        assertFalse(MitigationEngine.rejectHover(90, 80, -0.2));
        assertFalse(MitigationEngine.rejectHover(10, 80, 0.0));
    }

    @Test
    void speedRejectsOverCapBelowTeleport() {
        assertTrue(MitigationEngine.rejectSpeed(2.0, 1.3, 6.0));
        assertFalse(MitigationEngine.rejectSpeed(0.3, 1.3, 6.0));
        assertFalse(MitigationEngine.rejectSpeed(8.0, 1.3, 6.0));
    }

    @Test
    void teleportRespectsGraceButNotStall() {
        assertTrue(MitigationEngine.rejectTeleport(10.0, 6.0, false, 50L));
        assertFalse(MitigationEngine.rejectTeleport(10.0, 6.0, true, 50L));
        assertTrue(MitigationEngine.rejectTeleport(10.0, 6.0, false, 1500L));
        assertFalse(MitigationEngine.rejectTeleport(2.0, 6.0, false, 50L));
    }
}

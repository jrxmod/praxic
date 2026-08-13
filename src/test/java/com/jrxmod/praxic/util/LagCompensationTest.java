package com.jrxmod.praxic.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies latency scaling and the 500 ms clamp in LagCompensation.
 */
class LagCompensationTest {

    @Test
    void extraAirTicksScalesWithLatency() {
        assertEquals(0, LagCompensation.extraAirTicks(0));
        assertEquals(5, LagCompensation.extraAirTicks(250));
        assertEquals(10, LagCompensation.extraAirTicks(500));
    }

    @Test
    void extraAirTicksCapsAt500ms() {
        assertEquals(10, LagCompensation.extraAirTicks(1000));
    }

    @Test
    void extraSpeedScalesAndCaps() {
        assertEquals(0.0, LagCompensation.extraSpeed(0), 1e-9);
        assertEquals(0.3, LagCompensation.extraSpeed(500), 1e-9);
        assertEquals(0.3, LagCompensation.extraSpeed(1000), 1e-9);
    }

    @Test
    void extraReachScalesAndCaps() {
        assertEquals(0.0, LagCompensation.extraReach(0), 1e-9);
        assertEquals(1.0, LagCompensation.extraReach(500), 1e-9);
        assertEquals(1.0, LagCompensation.extraReach(1000), 1e-9);
    }
}

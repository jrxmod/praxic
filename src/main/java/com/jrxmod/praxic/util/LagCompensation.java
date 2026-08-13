package com.jrxmod.praxic.util;

import com.jrxmod.praxic.manager.CheckManager;

public class LagCompensation {

    // Latency above this value is clamped — prevents ping spoofing abuse
    private static final int LATENCY_CAP_MS = 500;

    // Extra air ticks allowed per 50ms of latency (max +10 ticks at 500ms)
    public static int extraAirTicks(int latencyMs) {
        return Math.min(latencyMs, LATENCY_CAP_MS) / 50;
    }

    // Extra blocks/tick allowed for speed (max +0.3 at 500ms)
    public static double extraSpeed(int latencyMs) {
        return Math.min(latencyMs, LATENCY_CAP_MS) * 0.0006;
    }

    // Extra reach distance allowed (max +1.0 block at 500ms)
    public static double extraReach(int latencyMs) {
        return Math.min(latencyMs, LATENCY_CAP_MS) * 0.002;
    }

    /**
     * Global sensitivity multiplier driven by server TPS.
     * At 20 TPS → 1.0 (no change). At 15 TPS → 1.25. At 10 TPS → 1.5.
     * Checks multiply their thresholds by this value to tolerate lag-induced
     * movement anomalies. Values below 17 TPS trigger scaling.
     */
    public static double tpsSensitivity() {
        double tps = CheckManager.getCurrentTps();
        if (tps >= 17.0) return 1.0;
        // Linear scale: at 17 TPS → 1.0, at 10 TPS → 1.5
        return 1.0 + (17.0 - tps) * 0.07;
    }
}

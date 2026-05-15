package com.jrxmod.praxic.util;

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
}

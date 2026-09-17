package com.jrxmod.praxic.engine.analysis;

/**
 * Immutable result of TimingAnalyzer for a single tick.
 * Consumed by timing-based detection checks.
 */
public final class TimingProfile {

    /**
     * Standard deviation of click intervals over the last 50 attacks (ms).
     * Human: σ = 7-15 ms. AutoClicker: σ < 3 ms.
     * -1.0 if fewer than 10 samples collected.
     */
    public final double clickIntervalStdDev;

    /**
     * Standard deviation of position-packet intervals over the last 100 packets (ms).
     * Human: σ = 3-5 ms. Timer/SpeedHack: σ < 1 ms.
     * -1.0 if fewer than 20 samples collected.
     */
    public final double packetIntervalStdDev;

    /** Average CPS computed from attack timestamps (combat CPS). */
    public final double avgCps;

    /** Average CPS from all click timestamps (attack + block + use) for UI display. */
    public final double totalCps;

    /** Number of attack interval samples collected (max 50). */
    public final int attackSamples;

    /** Number of packet interval samples collected (max 100). */
    public final int packetSamples;

    public TimingProfile(
            double clickIntervalStdDev,
            double packetIntervalStdDev,
            double avgCps,
            int attackSamples,
            int packetSamples
    ) {
        this(clickIntervalStdDev, packetIntervalStdDev, avgCps, avgCps, attackSamples, packetSamples);
    }

    public TimingProfile(
            double clickIntervalStdDev,
            double packetIntervalStdDev,
            double avgCps,
            double totalCps,
            int attackSamples,
            int packetSamples
    ) {
        this.clickIntervalStdDev  = clickIntervalStdDev;
        this.packetIntervalStdDev = packetIntervalStdDev;
        this.avgCps               = avgCps;
        this.totalCps             = totalCps;
        this.attackSamples        = attackSamples;
        this.packetSamples        = packetSamples;
    }
}

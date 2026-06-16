package com.jrxmod.praxic.engine.analysis;

import com.jrxmod.praxic.data.PlayerData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses player input timing patterns.
 * Produces TimingProfile consumed by detection checks.
 *
 * Metrics:
 *   - Standard deviation of click intervals (50-attack window)
 *   - Standard deviation of position-packet intervals (100-packet window)
 *   - Average CPS
 *
 * Data source: PlayerData.attackTimestamps / movePacketTimestamps
 * (sliding windows already maintained by AutoClickerCheck / TimerCheck).
 * TimingAnalyzer reads from these Deques — does not own them.
 */
public class TimingAnalyzer {

    // -------------------------------------------------------------------------
    // Window sizes
    // -------------------------------------------------------------------------

    private static final int ATTACK_WINDOW = 50;
    private static final int PACKET_WINDOW = 100;

    /** Minimum samples required before returning a meaningful σ. */
    private static final int MIN_ATTACK_SAMPLES = 10;
    private static final int MIN_PACKET_SAMPLES = 20;

    // -------------------------------------------------------------------------
    // Per-player derived interval windows
    // -------------------------------------------------------------------------

    /**
     * Intervals between consecutive attacks (ms), derived from attackTimestamps.
     * Maintained internally — avoids recomputing from the full Deque each tick.
     */
    private final Map<UUID, Deque<Long>> attackIntervals = new HashMap<>();

    /** Last attack timestamp seen — used as reference point for the next interval. */
    private final Map<UUID, Long> lastAttackTs = new HashMap<>();

    /**
     * Intervals between consecutive position packets (ms).
     */
    private final Map<UUID, Deque<Long>> packetIntervals = new HashMap<>();

    /** Last packet timestamp seen. */
    private final Map<UUID, Long> lastPacketTs = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Analyses timing for one player tick.
     * Call after CheckManager has processed packets for this tick.
     *
     * @param uuid player UUID
     * @param data legacy PlayerData — source of timestamp Deques
     * @return TimingProfile with all metrics
     */
    public TimingProfile analyse(UUID uuid, PlayerData data) {
        updateAttackIntervals(uuid, data);
        updatePacketIntervals(uuid, data);

        Deque<Long> aIntervals = attackIntervals.getOrDefault(uuid, new ArrayDeque<>());
        Deque<Long> pIntervals = packetIntervals.getOrDefault(uuid, new ArrayDeque<>());

        double clickStdDev  = stdDev(aIntervals, MIN_ATTACK_SAMPLES);
        double packetStdDev = stdDev(pIntervals, MIN_PACKET_SAMPLES);
        double avgCps       = computeAvgCps(data.attackTimestamps);

        return new TimingProfile(
                clickStdDev,
                packetStdDev,
                avgCps,
                aIntervals.size(),
                pIntervals.size()
        );
    }

    /** Removes all state for a player on disconnect. */
    public void reset(UUID uuid) {
        attackIntervals.remove(uuid);
        lastAttackTs.remove(uuid);
        packetIntervals.remove(uuid);
        lastPacketTs.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal — interval derivation
    // -------------------------------------------------------------------------

    /**
     * Derives new attack intervals from PlayerData.attackTimestamps.
     *
     * prev starts as lastSeen so that the first new timestamp produces
     * a cross-tick interval (ts - lastSeen), capturing the gap between
     * the last known attack and the first new one this tick.
     * Without this, single-attack-per-tick players would never accumulate
     * intervals and clickIntervalStdDev would stay at -1.0 indefinitely.
     */
    private void updateAttackIntervals(UUID uuid, PlayerData data) {
        if (data.attackTimestamps.isEmpty()) return;

        long lastSeen = lastAttackTs.getOrDefault(uuid, -1L);
        long prev     = lastSeen;   // reference point for the first new interval
        long newLast  = lastSeen;

        Deque<Long> intervals = attackIntervals.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        for (long ts : data.attackTimestamps) {
            if (ts <= lastSeen) continue;
            // prev >= 0 means we have a valid reference point to measure from
            if (prev >= 0) {
                long interval = ts - prev;
                if (interval > 0) {
                    intervals.addLast(interval);
                    while (intervals.size() > ATTACK_WINDOW) intervals.pollFirst();
                }
            }
            prev    = ts;
            newLast = ts;
        }

        if (newLast > lastSeen) lastAttackTs.put(uuid, newLast);
    }

    /**
     * Derives new packet intervals from PlayerData.movePacketTimestamps.
     * Same cross-tick interval logic as updateAttackIntervals.
     */
    private void updatePacketIntervals(UUID uuid, PlayerData data) {
        if (data.movePacketTimestamps.isEmpty()) return;

        long lastSeen = lastPacketTs.getOrDefault(uuid, -1L);
        long prev     = lastSeen;
        long newLast  = lastSeen;

        Deque<Long> intervals = packetIntervals.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        for (long ts : data.movePacketTimestamps) {
            if (ts <= lastSeen) continue;
            if (prev >= 0) {
                long interval = ts - prev;
                if (interval > 0) {
                    intervals.addLast(interval);
                    while (intervals.size() > PACKET_WINDOW) intervals.pollFirst();
                }
            }
            prev    = ts;
            newLast = ts;
        }

        if (newLast > lastSeen) lastPacketTs.put(uuid, newLast);
    }

    // -------------------------------------------------------------------------
    // Internal — statistics
    // -------------------------------------------------------------------------

    /**
     * Standard deviation of a Deque<Long>.
     * Returns -1.0 if fewer than minSamples values.
     */
    private static double stdDev(Deque<Long> values, int minSamples) {
        if (values.size() < minSamples) return -1.0;

        double mean = 0.0;
        for (long v : values) mean += v;
        mean /= values.size();

        double variance = 0.0;
        for (long v : values) {
            double diff = v - mean;
            variance += diff * diff;
        }
        variance /= values.size();
        return Math.sqrt(variance);
    }

    /**
     * Average CPS from a sliding 1-second timestamp window.
     * Returns 0 if window is empty.
     */
    private static double computeAvgCps(Deque<Long> timestamps) {
        if (timestamps.isEmpty()) return 0.0;
        long now   = System.currentTimeMillis();
        long count = timestamps.stream().filter(t -> now - t <= 1000L).count();
        return (double) count;
    }
}

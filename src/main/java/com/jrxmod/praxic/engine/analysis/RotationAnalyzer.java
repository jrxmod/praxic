package com.jrxmod.praxic.engine.analysis;

import com.jrxmod.praxic.engine.data.PlayerSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses player rotation patterns per tick.
 * Produces RotationProfile consumed by detection checks.
 *
 * Metrics:
 *   - Shannon entropy on deltaYaw (40-tick window)
 *   - Max snap angle (20-tick window)
 *   - Average rotation speed (40-tick window)
 *   - Post-kill snap angle (first 3 ticks after kill)
 */
public class RotationAnalyzer {

    // -------------------------------------------------------------------------
    // Window sizes
    // -------------------------------------------------------------------------

    /** Window for entropy and avgRotationSpeed. */
    private static final int ENTROPY_WINDOW = 40;

    /** Window for max snap angle. */
    private static final int SNAP_WINDOW = 20;

    /** Ticks after a kill event during which we track post-kill snap. */
    private static final int POST_KILL_WINDOW = 3;

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    /** Sliding window of absolute deltaYaw values for entropy calculation. */
    private final Map<UUID, Deque<Float>> yawWindow       = new HashMap<>();

    /** Sliding window of rotation speeds for avgRotationSpeed. */
    private final Map<UUID, Deque<Double>> speedWindow    = new HashMap<>();

    /** Sliding window of absolute deltaYaw for max snap angle. */
    private final Map<UUID, Deque<Float>> snapWindow      = new HashMap<>();

    /** Ticks remaining in the post-kill observation window. */
    private final Map<UUID, Integer> postKillTicks        = new HashMap<>();

    /** Max snap angle observed during post-kill window. */
    private final Map<UUID, Double> postKillMax           = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Analyses rotation for one player tick.
     * Call after SnapshotBuilder.build().
     *
     * @param uuid     player UUID
     * @param snapshot immutable state for this tick
     * @return RotationProfile with all metrics
     */
    public RotationProfile analyse(UUID uuid, PlayerSnapshot snapshot) {
        float  absDeltaYaw = Math.abs(snapshot.deltaYaw);
        double rotSpeed    = snapshot.rotationSpeed;

        // Update sliding windows
        addToWindow(yawWindow,   uuid, absDeltaYaw,  ENTROPY_WINDOW);
        addToWindow(speedWindow, uuid, rotSpeed,      ENTROPY_WINDOW);
        addToWindow(snapWindow,  uuid, absDeltaYaw,  SNAP_WINDOW);

        // Compute metrics
        double entropy        = computeEntropy(yawWindow.get(uuid));
        double maxSnap        = computeMax(snapWindow.get(uuid));
        double avgSpeed       = computeAvg(speedWindow.get(uuid));
        double postKillSnap   = computePostKill(uuid, absDeltaYaw);
        int    sampleCount    = yawWindow.getOrDefault(uuid, new ArrayDeque<>()).size();

        return new RotationProfile(entropy, maxSnap, avgSpeed, postKillSnap, sampleCount);
    }

    /**
     * Registers a kill event for this player.
     * Triggers the post-kill snap observation window.
     */
    public void onKill(UUID uuid) {
        postKillTicks.put(uuid, POST_KILL_WINDOW);
        postKillMax.put(uuid, -1.0);
    }

    /** Removes all state for a player on disconnect. */
    public void reset(UUID uuid) {
        yawWindow.remove(uuid);
        speedWindow.remove(uuid);
        snapWindow.remove(uuid);
        postKillTicks.remove(uuid);
        postKillMax.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal — window helpers
    // -------------------------------------------------------------------------

    private static <T> void addToWindow(Map<UUID, Deque<T>> map, UUID uuid, T value, int maxSize) {
        Deque<T> window = map.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        window.addLast(value);
        while (window.size() > maxSize) window.pollFirst();
    }

    // -------------------------------------------------------------------------
    // Internal — metric computation
    // -------------------------------------------------------------------------

    /**
     * Shannon entropy on a window of float values.
     * Values are bucketed into 36 bins of 10° each (0–360° range).
     * Returns -1.0 if window has fewer than ENTROPY_WINDOW samples.
     */
    private double computeEntropy(Deque<Float> window) {
        if (window == null || window.size() < ENTROPY_WINDOW) return -1.0;

        int[] bins = new int[36];
        for (float v : window) {
            int bin = Math.min((int)(v / 10f), 35);
            bins[bin]++;
        }

        double entropy = 0.0;
        int total = window.size();
        for (int count : bins) {
            if (count == 0) continue;
            double p = (double) count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** Returns the maximum value in the window, or 0 if empty. */
    private static double computeMax(Deque<Float> window) {
        if (window == null || window.isEmpty()) return 0.0;
        double max = 0.0;
        for (float v : window) if (v > max) max = v;
        return max;
    }

    /** Returns the average value in the window, or 0 if empty. */
    private static double computeAvg(Deque<Double> window) {
        if (window == null || window.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : window) sum += v;
        return sum / window.size();
    }

    /**
     * Tracks the max snap angle during the post-kill observation window.
     * Returns -1.0 outside the window.
     */
    private double computePostKill(UUID uuid, float absDeltaYaw) {
        int ticks = postKillTicks.getOrDefault(uuid, 0);
        if (ticks <= 0) return -1.0;

        postKillTicks.put(uuid, ticks - 1);
        double current = postKillMax.getOrDefault(uuid, -1.0);
        double updated = Math.max(current, absDeltaYaw);
        postKillMax.put(uuid, updated);
        return updated;
    }
}

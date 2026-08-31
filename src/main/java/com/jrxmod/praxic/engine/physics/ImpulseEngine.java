package com.jrxmod.praxic.engine.physics;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks short-lived legitimate motion impulses so movement checks can skip
 * or relax while vanilla physics is not gravity-only.
 *
 * Sources include damage knockback, explosions, wind charges, riptide, mace
 * smash knockback and vehicle eject. State is tick-based and independent of
 * Minecraft classes so the logic can be unit-tested.
 */
public class ImpulseEngine {

    public enum Kind {
        KNOCKBACK(20),
        EXPLOSION(30),
        WIND(35),
        RIPTIDE(40),
        MACE(20),
        VEHICLE_EJECT(15),
        PISTON(12);

        /** Default duration in server ticks. */
        public final int defaultTicks;

        Kind(int defaultTicks) {
            this.defaultTicks = defaultTicks;
        }
    }

    private static class State {
        final EnumMap<Kind, Integer> remaining = new EnumMap<>(Kind.class);
    }

    private final Map<UUID, State> states = new HashMap<>();

    /**
     * Records an impulse using the kind's default duration.
     * A later record of the same kind extends the window to at least that duration.
     */
    public void record(UUID uuid, Kind kind) {
        record(uuid, kind, kind.defaultTicks);
    }

    /**
     * Records an impulse for a specific number of ticks (minimum 1).
     */
    public void record(UUID uuid, Kind kind, int ticks) {
        if (uuid == null || kind == null) return;
        int duration = Math.max(1, ticks);
        State s = states.computeIfAbsent(uuid, id -> new State());
        int current = s.remaining.getOrDefault(kind, 0);
        s.remaining.put(kind, Math.max(current, duration));
    }

    /**
     * Decrements every active impulse for the player. Call once per server tick.
     */
    public void tick(UUID uuid) {
        State s = states.get(uuid);
        if (s == null) return;
        s.remaining.replaceAll((k, v) -> v > 0 ? v - 1 : 0);
        s.remaining.entrySet().removeIf(e -> e.getValue() <= 0);
        if (s.remaining.isEmpty()) states.remove(uuid);
    }

    /** True if any impulse is still active for the player. */
    public boolean isActive(UUID uuid) {
        State s = states.get(uuid);
        if (s == null) return false;
        for (int v : s.remaining.values()) {
            if (v > 0) return true;
        }
        return false;
    }

    /** True if a specific impulse kind is still active. */
    public boolean isActive(UUID uuid, Kind kind) {
        State s = states.get(uuid);
        if (s == null) return false;
        return s.remaining.getOrDefault(kind, 0) > 0;
    }

    /** Remaining ticks of the longest active impulse, or 0. */
    public int remaining(UUID uuid) {
        State s = states.get(uuid);
        if (s == null) return 0;
        int max = 0;
        for (int v : s.remaining.values()) {
            if (v > max) max = v;
        }
        return max;
    }

    /** Remaining ticks for one kind, or 0. */
    public int remaining(UUID uuid, Kind kind) {
        State s = states.get(uuid);
        if (s == null) return 0;
        return s.remaining.getOrDefault(kind, 0);
    }

    public void reset(UUID uuid) {
        states.remove(uuid);
    }
}

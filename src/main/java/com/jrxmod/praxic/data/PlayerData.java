package com.jrxmod.praxic.data;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {

    public double prevX;
    public double prevY;
    public double prevZ;

    // Timestamp of last position update in ms
    public long lastPositionUpdate = System.currentTimeMillis();

    // Air ticks counter for FlyCheck
    public int airTicks = 0;

    // Maximum fall distance tracked for NoFallCheck
    public double maxFallDistance = 0;

    // Health snapshot taken while player is still in air
    public float healthBeforeLanding = -1;

    // True if player was in air last tick
    public boolean wasInAir = false;

    // True if we need to verify fall damage on next tick
    public boolean pendingFallCheck = false;

    // Fall distance pending verification
    public double pendingFallDistance = 0;

    // Timestamp of last attack for KillAuraCheck
    public long lastAttackTime = 0;

    // Counter for rapid attacks within time window
    public int rapidAttackCount = 0;

    // Timestamp of rapid attack window start
    public long rapidAttackWindowStart = 0;

    public Map<String, Integer> violations = new HashMap<>();
    public Map<String, Long> lastFlagTime = new HashMap<>();

    public PlayerData(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }

    public int getViolations(String checkName) {
        return violations.getOrDefault(checkName, 0);
    }

    public void addViolation(String checkName) {
        violations.put(checkName, getViolations(checkName) + 1);
        lastFlagTime.put(checkName, System.currentTimeMillis());
    }

    public void resetViolations(String checkName) {
        violations.put(checkName, 0);
    }

    public boolean canFlag(String checkName, long cooldownMs) {
        long last = lastFlagTime.getOrDefault(checkName, 0L);
        return System.currentTimeMillis() - last >= cooldownMs;
    }

    public void updatePosition(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.lastPositionUpdate = System.currentTimeMillis();
    }
}

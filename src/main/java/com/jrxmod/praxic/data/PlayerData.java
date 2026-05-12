package com.jrxmod.praxic.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

public class PlayerData {

    public double prevX;
    public double prevY;
    public double prevZ;

    // Timestamp of last position update in ms
    public long lastPositionUpdate = System.currentTimeMillis();

    // Track ground state from previous tick
    public boolean wasOnGround = true;

    // Track water state to avoid false positives on water->land transitions
    public boolean wasInWater = false;

    // Grace period after leaving water (ticks)
    public int waterExitTicks = 0;

    // Air ticks counter for FlyCheck
    public int airTicks = 0;

    // Maximum fall distance tracked for NoFallCheck
    public double maxFallDistance = 0;

    // Total health (health + absorption) snapshot
    public float totalHealthBeforeLanding = -1;

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

    // Counter for blocks placed under feet within window for ScaffoldCheck
    public int scaffoldBlocksPlaced = 0;

    // Timestamp of scaffold detection window start
    public long scaffoldWindowStart = 0;

    // True if player had totem in hand last tick for AutoTotemCheck
    public boolean hadTotemInHand = false;

    // Timestamp when totem was consumed for AutoTotemCheck
    public long lastTotemUseTime = 0;

    // Counter for inventory clicks within window for InventoryCheck
    public int inventoryClickCount = 0;

    // Timestamp of inventory click detection window start
    public long inventoryWindowStart = 0;

    // Buffer for SpeedCheck to avoid flagging single-tick spikes
    public int speedBuffer = 0;

    // Sliding window of attack timestamps (ms) for AutoClickerCheck CPS calculation
    public final Deque<Long> attackTimestamps = new ArrayDeque<>();

    // Sliding window of movement packet timestamps (ms) for TimerCheck
    public final Deque<Long> movePacketTimestamps = new ArrayDeque<>();

    // Timestamp when player started breaking a block for FastBreakCheck
    public long breakStartTime = 0;

    // Position of block being broken for FastBreakCheck
    public BlockPos breakingBlockPos = null;

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

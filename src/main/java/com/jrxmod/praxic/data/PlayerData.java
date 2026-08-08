package com.jrxmod.praxic.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

public class PlayerData {

    // -------------------------------------------------------------------------
    // Movement State Machine
    // -------------------------------------------------------------------------

    /** Current movement state, computed by CheckManager before checks run. */
    public MovementState movementState = MovementState.GROUND;

    /** Movement state from the previous tick. */
    public MovementState prevMovementState = MovementState.GROUND;

    // -------------------------------------------------------------------------
    // Position
    // -------------------------------------------------------------------------

    public double prevX;
    public double prevY;
    public double prevZ;

    /** Last known safe position (on ground, not dead) for setback action. */
    public double lastSafeX;
    public double lastSafeY;
    public double lastSafeZ;

    /** Timestamp of last position update in ms. */
    public long lastPositionUpdate = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Rotation (updated by CheckManager after checks run)
    // -------------------------------------------------------------------------

    /** Yaw from the previous tick — used by RotationCheck. */
    public float lastYaw = 0f;

    /** Pitch from the previous tick — used by RotationCheck. */
    public float lastPitch = 0f;

    // -------------------------------------------------------------------------
    // Derived legacy fields — set by CheckManager from the state machine.
    // Kept for backward compatibility with existing checks.
    // -------------------------------------------------------------------------

    /** True if player was on GROUND state last tick. */
    public boolean wasOnGround = true;

    /** True if player was in WATER state last tick. */
    public boolean wasInWater = false;

    /**
     * Grace ticks after leaving water — used by FlyCheck.
     * Managed by CheckManager, not by individual checks.
     */
    public int waterExitTicks = 0;

    /**
     * Independent grace ticks after leaving water — used by JesusCheck.
     * Managed by CheckManager, not by individual checks.
     */
    public int jesusWaterGraceTicks = 0;

    /** Consecutive ticks spent airborne (JUMP / AIR / FALLING). Reset on GROUND / WATER / CLIMB. */
    public int airTicks = 0;

    // -------------------------------------------------------------------------
    // Join grace
    // -------------------------------------------------------------------------

    /**
     * Grace ticks after joining the server.
     * Set to 40 on join, decremented each tick by CheckManager.
     * Checks that are sensitive to the first-tick state should skip while > 0.
     */
    public int joinGraceTicks = 40;

    // -------------------------------------------------------------------------
    // Fall tracking
    // -------------------------------------------------------------------------

    /** Maximum fall distance tracked for NoFallCheck. */
    public double maxFallDistance = 0;

    /** Total health (health + absorption) snapshot. */
    public float totalHealthBeforeLanding = -1;

    /** True if player was in air last tick. */
    public boolean wasInAir = false;

    /** True if we need to verify fall damage on next tick. */
    public boolean pendingFallCheck = false;

    /** Fall distance pending verification. */
    public double pendingFallDistance = 0;

    /** Block position at the moment of landing, for safe-landing checks. */
    public BlockPos pendingFallPos = null;

    // -------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------

    /** Timestamp of last attack for KillAuraCheck and RotationCheck. */
    public long lastAttackTime = 0;

    /** Counter for rapid attacks within time window. */
    public int rapidAttackCount = 0;

    /** Timestamp of rapid attack window start. */
    public long rapidAttackWindowStart = 0;

    // -------------------------------------------------------------------------
    // Scaffold / AutoTotem / Inventory
    // -------------------------------------------------------------------------

    /** Timestamp of the last damage received (hurtTime > 0), for AutoTotemCheck. */
    public long lastDamageTime = 0;

    /** Timestamp of the last firework rocket use, for ElytraFlyCheck. */
    public long lastRocketUseTime = 0;

    /** Counter for blocks placed under feet within window for ScaffoldCheck. */
    public int scaffoldBlocksPlaced = 0;

    /** Timestamp of scaffold detection window start. */
    public long scaffoldWindowStart = 0;

    /** True if player had totem in hand last tick for AutoTotemCheck. */
    public boolean hadTotemInHand = false;

    /** Timestamp when totem was consumed for AutoTotemCheck. */
    public long lastTotemUseTime = 0;

    /** Counter for inventory clicks within window for InventoryCheck. */
    public int inventoryClickCount = 0;

    /** Timestamp of inventory click detection window start. */
    public long inventoryWindowStart = 0;

    // -------------------------------------------------------------------------
    // Speed / AutoClicker / Timer / FastBreak
    // -------------------------------------------------------------------------

    /** Buffer for SpeedCheck to avoid flagging single-tick spikes. */
    public int speedBuffer = 0;

    /** Sliding window of attack timestamps (ms) for AutoClickerCheck CPS calculation. */
    public final Deque<Long> attackTimestamps = new ArrayDeque<>();

    /** Sliding window of movement packet timestamps (ms) for TimerCheck. */
    public final Deque<Long> movePacketTimestamps = new ArrayDeque<>();

    /** Timestamp when player started breaking a block for FastBreakCheck. */
    public long breakStartTime = 0;

    /** Position of block being broken for FastBreakCheck. */
    public BlockPos breakingBlockPos = null;

    // -------------------------------------------------------------------------
    // Velocity / Knockback
    // -------------------------------------------------------------------------

    /** hurtTime value from previous tick for VelocityCheck hit detection. */
    public int prevHurtTime = 0;

    /** True if waiting to evaluate knockback displacement. */
    public boolean knockbackPending = false;

    /** Player position at the moment of hit for VelocityCheck. */
    public double knockbackStartX;
    public double knockbackStartZ;

    /** Ticks elapsed since knockback was registered. */
    public int knockbackTicksWaited = 0;

    // -------------------------------------------------------------------------
    // Combat / Client buffers
    // -------------------------------------------------------------------------

    /**
     * Consecutive ticks where a suspicious snap angle was detected during combat.
     * Incremented on snap, decremented when clean — flags at threshold.
     */
    public int rotationSnapBuffer = 0;

    /** Buffer for spoofed critical-hit packets. */
    public int criticalsBuffer = 0;

    /** Buffer for impossible / malformed movement packets. */
    public int badPacketBuffer = 0;

    // -------------------------------------------------------------------------
    // Movement buffers
    // -------------------------------------------------------------------------

    /** Buffer for moving too fast while using slowed items. */
    public int noSlowBuffer = 0;

    /** Consecutive ticks spent moving inside solid collision. */
    public int phaseTicks = 0;

    // -------------------------------------------------------------------------
    // BoatFlyCheck
    // -------------------------------------------------------------------------

    /**
     * Consecutive ticks the player's boat vehicle has been hovering airborne
     * (not falling naturally, not on ground, not in water).
     */
    public int boatAirTicks = 0;

    // -------------------------------------------------------------------------
    // ElytraFlyCheck
    // -------------------------------------------------------------------------

    public int elytraAirTicks = 0;
    public int elytraBuffer = 0;
    public double lastElytraY = 0;
    public boolean wasFallFlying = false;

    // -------------------------------------------------------------------------
    // StepCheck
    // -------------------------------------------------------------------------

    public double lastGroundY = 0;
    public int stepBuffer = 0;

    // -------------------------------------------------------------------------
    // TowerCheck / FastPlaceCheck
    // -------------------------------------------------------------------------

    public int towerBlockCount = 0;
    public long towerWindowStart = 0;
    public double towerLastY = 0;
    public int fastPlaceCount = 0;
    public long fastPlaceWindowStart = 0;

    // -------------------------------------------------------------------------
    // GroundSpoofCheck
    // -------------------------------------------------------------------------

    public boolean lastPacketOnGround = false;
    public boolean lastPacketHasPos = false;
    public int groundSpoofTicks = 0;
    public int groundSpoofBuffer = 0;

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------

    public int totalTicks = 0;

    // -------------------------------------------------------------------------
    // Violations
    // -------------------------------------------------------------------------

    public Map<String, Integer> violations = new HashMap<>();
    public Map<String, Long> lastFlagTime = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PlayerData(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.lastSafeX = x;
        this.lastSafeY = y;
        this.lastSafeZ = z;
    }

    // -------------------------------------------------------------------------
    // Violation helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Position helpers
    // -------------------------------------------------------------------------

    public void updatePosition(double x, double y, double z) {
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.lastPositionUpdate = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // VL Decay
    // -------------------------------------------------------------------------

    /** Decay all violations by 1 VL per check if no flag for decayIntervalMs. */
    public void decayViolations(long decayIntervalMs) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> entry : violations.entrySet()) {
            int vl = entry.getValue();
            if (vl <= 0) continue;
            long last = lastFlagTime.getOrDefault(entry.getKey(), 0L);
            if (now - last >= decayIntervalMs) {
                entry.setValue(vl - 1);
            }
        }
    }
}

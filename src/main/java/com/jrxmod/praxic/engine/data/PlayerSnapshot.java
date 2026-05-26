package com.jrxmod.praxic.engine.data;

import com.jrxmod.praxic.data.MovementState;

/**
 * Immutable snapshot of all relevant player state for a single server tick.
 * Built by SnapshotBuilder after syncDerivedFields(), before checks run.
 * All engine layers operate on this object — never on ServerPlayer directly.
 */
public final class PlayerSnapshot {

    // -------------------------------------------------------------------------
    // Time
    // -------------------------------------------------------------------------

    /** Wall-clock timestamp when this snapshot was built (ms). */
    public final long timestamp;

    // -------------------------------------------------------------------------
    // Position — current tick
    // -------------------------------------------------------------------------

    public final double x;
    public final double y;
    public final double z;

    // -------------------------------------------------------------------------
    // Position — previous tick
    // -------------------------------------------------------------------------

    public final double prevX;
    public final double prevY;
    public final double prevZ;

    // -------------------------------------------------------------------------
    // Deltas and speeds (derived, computed in constructor)
    // -------------------------------------------------------------------------

    /** Horizontal displacement X this tick. */
    public final double dx;

    /** Vertical displacement this tick. */
    public final double dy;

    /** Horizontal displacement Z this tick. */
    public final double dz;

    /** Horizontal speed (XZ magnitude). */
    public final double speed;

    /** Vertical speed — alias for dy, for clarity in physics code. */
    public final double verticalSpeed;

    // -------------------------------------------------------------------------
    // Rotation — current tick
    // -------------------------------------------------------------------------

    public final float yaw;
    public final float pitch;

    // -------------------------------------------------------------------------
    // Rotation — previous tick
    // -------------------------------------------------------------------------

    public final float prevYaw;
    public final float prevPitch;

    // -------------------------------------------------------------------------
    // Rotation deltas (wraparound-corrected, computed in constructor)
    // -------------------------------------------------------------------------

    /**
     * Yaw change this tick, corrected for 359°→1° wraparound.
     * Range: (-180, +180].
     */
    public final float deltaYaw;

    /**
     * Pitch change this tick, corrected for wraparound.
     * Range: (-180, +180].
     */
    public final float deltaPitch;

    /** Combined rotation magnitude: sqrt(deltaYaw² + deltaPitch²). */
    public final double rotationSpeed;

    // -------------------------------------------------------------------------
    // Movement flags
    // -------------------------------------------------------------------------

    public final boolean onGround;
    public final boolean inWater;
    public final boolean onClimbable;

    /** True while gliding with elytra — trajectory is not gravitational. */
    public final boolean fallFlying;

    /** True while riding any vehicle. */
    public final boolean passenger;

    // -------------------------------------------------------------------------
    // Movement state
    // -------------------------------------------------------------------------

    public final MovementState movementState;
    public final MovementState prevMovementState;

    // -------------------------------------------------------------------------
    // Combat / damage
    // -------------------------------------------------------------------------

    /** > 0 when player received any damage this tick or recently. */
    public final int hurtTime;

    /** Current health (does not include absorption). */
    public final float health;

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    /** Player ping in ms, capped at 500. */
    public final int ping;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PlayerSnapshot(
            long timestamp,
            double x,    double y,    double z,
            double prevX, double prevY, double prevZ,
            float yaw,   float pitch,
            float prevYaw, float prevPitch,
            boolean onGround, boolean inWater, boolean onClimbable,
            boolean fallFlying, boolean passenger,
            MovementState movementState, MovementState prevMovementState,
            int hurtTime, float health,
            int ping
    ) {
        this.timestamp = timestamp;

        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = prevX;
        this.prevY = prevY;
        this.prevZ = prevZ;

        // Compute deltas
        this.dx = x - prevX;
        this.dy = y - prevY;
        this.dz = z - prevZ;

        this.speed        = Math.sqrt(dx * dx + dz * dz);
        this.verticalSpeed = dy;

        this.yaw      = yaw;
        this.pitch    = pitch;
        this.prevYaw  = prevYaw;
        this.prevPitch = prevPitch;

        // Wraparound-corrected yaw delta
        float rawYaw = yaw - prevYaw;
        if      (rawYaw >  180f) rawYaw -= 360f;
        else if (rawYaw < -180f) rawYaw += 360f;
        this.deltaYaw = rawYaw;

        // Wraparound-corrected pitch delta
        float rawPitch = pitch - prevPitch;
        if      (rawPitch >  180f) rawPitch -= 360f;
        else if (rawPitch < -180f) rawPitch += 360f;
        this.deltaPitch = rawPitch;

        this.rotationSpeed = Math.sqrt(
                (double)(deltaYaw * deltaYaw) + (double)(deltaPitch * deltaPitch)
        );

        this.onGround    = onGround;
        this.inWater     = inWater;
        this.onClimbable = onClimbable;
        this.fallFlying  = fallFlying;
        this.passenger   = passenger;

        this.movementState     = movementState;
        this.prevMovementState = prevMovementState;

        this.hurtTime = hurtTime;
        this.health   = health;

        this.ping = ping;
    }
}

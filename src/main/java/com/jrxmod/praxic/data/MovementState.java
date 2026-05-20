package com.jrxmod.praxic.data;

/**
 * Represents the discrete movement phase of a player, updated once per tick
 * in CheckManager before any checks run. All checks should read this value
 * instead of maintaining their own state booleans.
 */
public enum MovementState {

    /** Standing on solid ground, not in water or on a climbable. */
    GROUND,

    /** Just left the ground while ascending (grace: up to 2 ticks). */
    JUMP,

    /** Airborne and ascending or neutral (dy >= 0), not a fresh jump. */
    AIR,

    /** Airborne and descending (dy < 0). */
    FALLING,

    /** Submerged in or touching water. */
    WATER,

    /** On a ladder, vine, or other climbable surface. */
    CLIMB
}

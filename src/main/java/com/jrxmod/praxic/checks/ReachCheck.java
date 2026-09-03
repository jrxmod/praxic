package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Detects extended attack reach and attacks through solid blocks.
 *
 * The server validates an attack packet in 1.21.1 with
 * {@code player.canInteractWithEntity(aabb, 1.0)} (ServerGamePacketListenerImpl
 * .handleInteract): the eye-to-closest-AABB-point distance must be below
 * {@code entityInteractionRange + 1.0}, i.e. up to 4.0 blocks in survival.
 * A measured distance of 3.0-3.9 is therefore vanilla-legal regardless of
 * jumping, spam clicking or a moving target; old builds that flagged 3.1-3.5
 * produced false positives. Only values above the vanilla cap are cheats.
 *
 * Distance is measured to the closest point of the target's bounding box from
 * the attacker's eye, matching vanilla semantics. A small margin plus the
 * forward component of the last-tick movement covers client/server frame
 * skew, and the target box is rewound by its own motion during the processing
 * delay (a knocked-back or running mob was attacked at an earlier position).
 *
 * A ray-based distance measurement was abandoned: the look ray can hit the
 * far face of a target hitbox, producing a false overshoot.
 */
public class ReachCheck extends AbstractCheck {

    /** Fixed allowance on top of the vanilla cap (range + 1.0). */
    private static final double REACH_MARGIN = 0.05;

    /** Forward movement allowance cap (frame skew while moving). */
    private static final double MAX_FORWARD_ALLOWANCE = 0.10;

    // A block must lie at least this far in front of the target before it is
    // treated as a wall, so a block flush with the target surface is ignored.
    private static final double WALL_EPSILON = 0.2;

    // Consecutive wall-hit ticks required before flagging.
    private static final int WALL_BUFFER = 2;

    @Override
    public String getName() {
        return "ReachCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven check — called from ServerGamePacketListenerMixin
    }

    /**
     * @return true if the attack packet should be cancelled
     */
    public boolean checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {
        if (!Praxic.getConfig().reachCheckEnabled) return false;
        if (attacker.isSpectator()) return false;
        if (attacker.isDeadOrDying()) return false;
        if (target == null || target.isRemoved()) return false;

        double allowance = forwardMovementAllowance(attacker, data);
        // Vanilla validation: canInteractWithEntity(aabb, 1.0)  -  eye to the
        // closest box point must stay below entityInteractionRange + 1.0.
        double maxReach = attacker.entityInteractionRange() + 1.0 + REACH_MARGIN
                + LagCompensation.extraReach(attacker.connection.latency())
                + allowance;

        Vec3 eye = attacker.getEyePosition();
        // The client attacks against its own view of the entity; the server
        // processes the packet a moment later, so a moving target (knockback,
        // sprinting mob) is measured from its later position. Rewind the box
        // by the motion covered by the processing delay, capped at safe value.
        AABB box = target.getBoundingBox();
        Vec3 vel = target.getDeltaMovement();
        double windowSec = 0.05 + attacker.connection.latency() / 1000.0;
        double motion = Math.sqrt(vel.x * vel.x + vel.y * vel.y + vel.z * vel.z) * windowSec;
        if (motion > 0.01 && motion < 0.45) {
            box = box.move(-vel.x * windowSec, -vel.y * windowSec, -vel.z * windowSec);
        }
        Vec3 closest = new Vec3(
                clamp(eye.x, box.minX, box.maxX),
                clamp(eye.y, box.minY, box.maxY),
                clamp(eye.z, box.minZ, box.maxZ)
        );
        double distance = eye.distanceTo(closest);

        if (distance > maxReach) {
            if (data.canFlag(getName(), 1500)) {
                ViolationManager.flag(attacker, data, this,
                        String.format("Attack distance: %.2f blocks (max: %.2f, move allow: %.2f, ping: %dms)",
                                distance, maxReach, allowance,
                                attacker.connection.latency()));
            }
            return Praxic.getConfig().enableMitigation;
        }

        return checkThroughWall(attacker, data, eye, closest, distance);
    }

    /**
     * Forward projection of the last-tick movement onto the current look
     * direction: a moving attacker is legitimately closer than the position
     * the server last saw when the attack packet is processed.
     */
    private static double forwardMovementAllowance(ServerPlayer attacker, PlayerData data) {
        double mx = attacker.getX() - data.prevX;
        double my = attacker.getY() - data.prevY;
        double mz = attacker.getZ() - data.prevZ;
        double horizontal = Math.sqrt(mx * mx + mz * mz);
        if (horizontal < 0.01) return 0.0;

        Vec3 look = lookFrom(attacker.getYRot(), attacker.getXRot());
        double forward = mx * look.x + my * look.y + mz * look.z;
        return Math.max(0.0, Math.min(MAX_FORWARD_ALLOWANCE, forward));
    }

    /** Vanilla calculateViewVector, in degrees. */
    private static Vec3 lookFrom(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(-yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        return new Vec3(Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /**
     * Flags attacks whose line of sight to the target passes through a full
     * solid block. Thin blocks (fences, panes, bars) are ignored because they
     * are legitimately attackable through.
     */
    private boolean checkThroughWall(ServerPlayer attacker, PlayerData data,
                                  Vec3 eye, Vec3 closest, double distance) {
        ClipContext ctx = new ClipContext(eye, closest,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker);
        BlockHitResult hit = attacker.level().clip(ctx);

        if (hit.getType() != HitResult.Type.BLOCK) {
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return false;
        }

        double blockDistance = hit.getLocation().distanceTo(eye);
        if (blockDistance >= distance - WALL_EPSILON) {
            // The block is flush with the target surface, not between them.
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = attacker.level().getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(attacker.level(), pos);
        if (!Block.isShapeFullBlock(shape)) {
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return false;
        }

        data.reachWallBuffer++;
        if (data.reachWallBuffer >= WALL_BUFFER && data.canFlag(getName() + "_wall", 2000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack through wall at %d,%d,%d (%.2f blocks)",
                            pos.getX(), pos.getY(), pos.getZ(), blockDistance));
            data.reachWallBuffer = 0;
            return Praxic.getConfig().enableMitigation;
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}

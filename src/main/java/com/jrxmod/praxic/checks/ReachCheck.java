package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
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
 * Distance is measured to the closest point of the target's bounding box rather
 * than its centre, matching vanilla semantics and removing the hitbox-size bias
 * that previously forced a loose threshold. A separate raycast flags attacks
 * where a full solid block lies between the attacker and the target, which is
 * the signature of reach-through-wall modules.
 */
public class ReachCheck extends AbstractCheck {

    // Vanilla attack reach is 3.0 (survival) and 5.0 (creative). The small
    // buffers cover server tick timing and hitbox rounding; latency is added
    // on top via LagCompensation.
    private static final double MAX_REACH_SURVIVAL = 3.5;
    private static final double MAX_REACH_CREATIVE = 5.5;

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

    public void checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {
        if (!Praxic.getConfig().reachCheckEnabled) return;
        if (attacker.isSpectator()) return;
        if (attacker.isDeadOrDying()) return;

        boolean isCreative = attacker.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        double maxReach = isCreative ? MAX_REACH_CREATIVE : MAX_REACH_SURVIVAL;
        maxReach += LagCompensation.extraReach(attacker.connection.latency());

        Vec3 eye = attacker.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 closest = new Vec3(
                clamp(eye.x, box.minX, box.maxX),
                clamp(eye.y, box.minY, box.maxY),
                clamp(eye.z, box.minZ, box.maxZ)
        );
        double distance = eye.distanceTo(closest);

        if (distance > maxReach && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack distance: %.2f blocks (max: %.2f, ping: %dms)",
                            distance, maxReach, attacker.connection.latency()));
            return;
        }

        checkThroughWall(attacker, data, eye, closest, distance);
    }

    /**
     * Flags attacks whose line of sight to the target passes through a full
     * solid block. Thin blocks (fences, panes, bars) are ignored because they
     * are legitimately attackable through.
     */
    private void checkThroughWall(ServerPlayer attacker, PlayerData data,
                                  Vec3 eye, Vec3 closest, double distance) {
        ClipContext ctx = new ClipContext(eye, closest,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker);
        BlockHitResult hit = attacker.level().clip(ctx);

        if (hit.getType() != HitResult.Type.BLOCK) {
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return;
        }

        double blockDistance = hit.getLocation().distanceTo(eye);
        if (blockDistance >= distance - WALL_EPSILON) {
            // The block is flush with the target surface, not between them.
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = attacker.level().getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(attacker.level(), pos);
        if (!Block.isShapeFullBlock(shape)) {
            data.reachWallBuffer = Math.max(0, data.reachWallBuffer - 1);
            return;
        }

        data.reachWallBuffer++;
        if (data.reachWallBuffer >= WALL_BUFFER && data.canFlag(getName() + "_wall", 2000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack through wall at %d,%d,%d (%.2f blocks)",
                            pos.getX(), pos.getY(), pos.getZ(), blockDistance));
            data.reachWallBuffer = 0;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}

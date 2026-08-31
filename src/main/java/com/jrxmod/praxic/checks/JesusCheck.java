package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Jesus packet-spoofs {@code onGround=true} and Y±0.05 only while
 * {@code !isInWater} and over liquid. Surface swimming and water-exit are
 * {@code onGround=false} and/or still {@code isInWater}.
 */
public class JesusCheck extends AbstractCheck {

    private static final double MIN_HORIZONTAL_SPEED = 0.05;
    private static final int BUFFER_THRESHOLD = 4;

    @Override
    public String getName() {
        return "JesusCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().jesusCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.getAbilities().mayfly) return;
        if (player.isFallFlying()) return;
        if (player.isSwimming() || player.isUnderWater() || player.isInWater() || player.isInLava()) {
            data.jesusBuffer = 0;
            return;
        }
        if (player.hurtTime > 0) return;
        if (data.jesusWaterGraceTicks > 0) return;
        if (data.wasInWater) return;
        if (player.isShiftKeyDown()) {
            data.jesusBuffer = 0;
            return;
        }

        double dy = player.getY() - data.prevY;
        if (dy < -0.01) {
            data.jesusBuffer = 0;
            return;
        }

        BlockPos foot = player.blockPosition();
        BlockPos below = foot.below();
        if (isLilyOrBubble(player, foot) || isLilyOrBubble(player, below)) {
            data.jesusBuffer = 0;
            return;
        }
        if (player.level().getBlockState(below).is(Blocks.FROSTED_ICE)) {
            data.jesusBuffer = 0;
            return;
        }
        if (isSolidFloor(player, below) && !isLiquid(player, below)) {
            data.jesusBuffer = 0;
            return;
        }
        if (!isLiquid(player, foot) && !isLiquid(player, below)) {
            data.jesusBuffer = 0;
            return;
        }

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < MIN_HORIZONTAL_SPEED) {
            data.jesusBuffer = Math.max(0, data.jesusBuffer - 1);
            return;
        }

        data.jesusBuffer++;
        if (data.jesusBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Walking on water surface, speed=%.3f", horizontal));
            data.jesusBuffer = 0;
        }
    }

    /**
     * Packet-level: water-walk rewrites 1 in 4 position packets to onGround=true
     * over liquid. Swimming / exiting water does not.
     */
    public void onMovePacket(ServerPlayer player,
                             net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,
                             PlayerData data) {
        if (!Praxic.getConfig().jesusCheckEnabled) return;
        if (!packet.hasPosition()) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isPassenger() || player.isFallFlying() || data.wasFallFlying || data.elytraAirTicks > 0) return;
        if (player.isSwimming() || player.isUnderWater() || player.isInWater() || player.isInLava()) {
            data.jesusBuffer = 0;
            return;
        }
        if (data.joinGraceTicks > 0 || data.jesusWaterGraceTicks > 0) return;
        if (player.isShiftKeyDown()) {
            data.jesusBuffer = 0;
            return;
        }
        if (!packet.isOnGround()) {
            return;
        }

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        BlockPos foot = BlockPos.containing(x, y, z);
        BlockPos below = foot.below();
        if (isLilyOrBubble(player, foot) || isLilyOrBubble(player, below)) {
            data.jesusBuffer = 0;
            return;
        }
        if (player.level().getBlockState(below).is(Blocks.FROSTED_ICE)) {
            data.jesusBuffer = 0;
            return;
        }
        if (isSolidFloor(player, below) && !isLiquid(player, below)) {
            data.jesusBuffer = 0;
            return;
        }
        if (!isLiquid(player, foot) && !isLiquid(player, below)) {
            data.jesusBuffer = Math.max(0, data.jesusBuffer - 1);
            return;
        }

        double horiz = 0.0;
        if (data.lastPacketTime != 0L) {
            double dx = x - data.lastPacketX;
            double dz = z - data.lastPacketZ;
            horiz = Math.sqrt(dx * dx + dz * dz);
        }
        if (horiz < MIN_HORIZONTAL_SPEED) {
            return;
        }

        data.jesusBuffer++;
        if (data.jesusBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Packet water-walk horiz=%.3f onGround=%s y=%.2f",
                            horiz, packet.isOnGround(), y));
            data.jesusBuffer = 0;
        }
    }

    private static boolean isLiquid(ServerPlayer player, BlockPos pos) {
        var fluid = player.level().getFluidState(pos);
        return fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)
                || fluid.is(Fluids.LAVA) || fluid.is(Fluids.FLOWING_LAVA);
    }

    private static boolean isLilyOrBubble(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        return state.is(Blocks.LILY_PAD) || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static boolean isSolidFloor(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        return !state.getCollisionShape(player.level(), pos).isEmpty();
    }
}

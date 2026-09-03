package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * NoSlowdown keeps near-sprint speed while vanilla scales use-item input by
 * 0.2. Only {@code isUsingItem()} counts, and only on the ground: a sticky
 * use-window plus sprint-in-air was flagging vanilla running and falling.
 */
public class NoSlowCheck extends AbstractCheck {

    private static final int BUFFER_THRESHOLD = 5;
    private static final int BUFFER_DECAY = 1;

    @Override
    public String getName() {
        return "NoSlowCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().noSlowCheckEnabled) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying() || player.getAbilities().mayfly) return;
        if (player.isPassenger() || player.isFallFlying()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (!player.onGround()) {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - BUFFER_DECAY);
            return;
        }
        if (player.hurtTime > 0 || data.joinGraceTicks > 0) return;
        if (Praxic.getImpulseEngine() != null && Praxic.getImpulseEngine().isActive(player.getUUID())) {
            data.noSlowBuffer = 0;
            return;
        }
        if (!player.isUsingItem()) {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - BUFFER_DECAY);
            return;
        }

        // The first ticks after pressing use still carry full sprint speed:
        // the client applies the use-item slowdown after the use packet is
        // processed. A single early flag on the "first bite" while eating on
        // the run was a false positive.
        if (data.lastItemUseTime > 0
                && System.currentTimeMillis() - data.lastItemUseTime < 300L) {
            data.noSlowBuffer = 0;
            return;
        }

        BlockPos below = player.blockPosition().below();
        if (isIce(player.level().getBlockState(below).getBlock())
                || isIce(player.level().getBlockState(below.north()).getBlock())
                || isIce(player.level().getBlockState(below.south()).getBlock())
                || isIce(player.level().getBlockState(below.east()).getBlock())
                || isIce(player.level().getBlockState(below.west()).getBlock())) {
            data.noSlowBuffer = 0;
            return;
        }

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int ping = player.connection.latency();
        double maxSpeed = (Praxic.getConfig().noSlowMaxBlocksPerTick
                + LagCompensation.extraSpeed(ping) * 0.35)
                * LagCompensation.tpsSensitivity();
        if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            int amplifier = player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
            maxSpeed *= (1.0 + 0.2 * (amplifier + 1));
        }
        // Low-FPS clients fold several ticks into one packet (same allowance
        // as SpeedCheck), otherwise a legit player at ~10 FPS is flagged.
        if (data.lastMoveGapMs > 80) {
            maxSpeed *= Math.min(3.0, data.lastMoveGapMs / 50.0);
        }

        if (horizontal > maxSpeed) {
            data.noSlowBuffer++;
        } else {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - BUFFER_DECAY);
        }

        if (data.noSlowBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Using item while moving %.3f b/t (max: %.3f, ping: %dms)",
                            horizontal, maxSpeed, ping));
            data.noSlowBuffer = 0;
        }
    }

    public void onMovePacket(ServerPlayer player,
                             net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,
                             PlayerData data) {
        if (!Praxic.getConfig().noSlowCheckEnabled) return;
        if (!packet.hasPosition() || data.lastPacketTime == 0L) return;
        if (!player.isUsingItem()) return;
        if (!packet.isOnGround()) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;
        // Same first-use grace as the tick path.
        if (data.lastItemUseTime > 0
                && System.currentTimeMillis() - data.lastItemUseTime < 300L) return;

        double x = packet.getX(player.getX());
        double z = packet.getZ(player.getZ());
        double horiz = Math.sqrt(
                (x - data.lastPacketX) * (x - data.lastPacketX)
                        + (z - data.lastPacketZ) * (z - data.lastPacketZ));
        // Same configurable threshold as the tick path; the packet path also
        // stays tolerant of the vanilla sprint-while-eating speed.
        double max = Praxic.getConfig().noSlowMaxBlocksPerTick;
        if (data.lastMoveGapMs > 80) {
            max *= Math.min(3.0, data.lastMoveGapMs / 50.0);
        }
        if (horiz <= max) {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - 1);
            return;
        }
        data.noSlowBuffer++;
        if (data.noSlowBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Packet NoSlow horiz=%.3f while using item", horiz));
            data.noSlowBuffer = 0;
        }
    }

    private boolean isIce(net.minecraft.world.level.block.Block block) {
        return block == Blocks.ICE
                || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE
                || block == Blocks.FROSTED_ICE;
    }
}

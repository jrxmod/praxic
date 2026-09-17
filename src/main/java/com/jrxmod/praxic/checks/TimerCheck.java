package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Client timer cheats multiply deltaTicks so the player covers ~2x vanilla
 * distance per wall-clock second. Extra packets often share one server tick,
 * so packets/sec stays ~20. A TPS-below-17 guard also skipped the whole check
 * on a loaded local server.
 *
 * Primary: XZ travelled over 20 server ticks (vanilla sprint ~5.6, sprint-jump
 * ~7, timer x2 sprint ~11). Secondary: 2+ position packets in many ticks, or
 * a sustained rolling packet rate above the configured cap.
 */
public class TimerCheck extends AbstractCheck {

    private static final int FAST_TICK_STREAK = 8;
    private static final int SPEED_SAMPLES = 20;
    private static final long RATE_WINDOW_MS = 1000L;
    private static final int RATE_STREAK = 3;
    /**
     * Vanilla grounded sprint is ~5.6 m/s, sprint-jump average ~7.1 m/s.
     * Previous 6.6 threshold flagged legitimate sprint-jumping.
     * 8.0 allows vanilla sprint-jump plus margin for diagonal movement
     * and minor lag, while timer x2.0 (11+ blocks/s) still exceeds it.
     * Based on vanilla travel physics.
     */
    private static final double MAX_VANILLA_METERS = 8.0;

    @Override
    public String getName() {
        return "TimerCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().timerCheckEnabled) {
            data.timerPacketsThisTick = 0;
            return;
        }
        if (player.isDeadOrDying() || player.isSpectator()
                || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            data.timerPacketsThisTick = 0;
            data.timerFastStreak = 0;
            data.timerSpeedSamples = 0;
            data.timerSpeedMeters = 0;
            return;
        }

        int movePacketsThisTick = data.timerPacketsThisTick;
        if (movePacketsThisTick >= 2) {
            data.timerFastStreak++;
        } else {
            data.timerFastStreak = Math.max(0, data.timerFastStreak - 1);
        }
        if (data.timerFastStreak >= FAST_TICK_STREAK && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Timer: %d ticks with 2+ move packets (n=%d)",
                            data.timerFastStreak, movePacketsThisTick));
            data.timerFastStreak = 0;
        }
        data.timerPacketsThisTick = 0;

        if (player.isPassenger() || player.isFallFlying() || player.isAutoSpinAttack()
                || player.isInWater() || player.isInLava()
                || player.hurtTime > 0
                || player.verticalCollision || player.horizontalCollision
                || data.joinGraceTicks > 0
                // A tick with several move packets  -  or a low-FPS client
                // folding several client ticks into one packet  -  sums more
                // than one client step; restart the sample window instead.
                || movePacketsThisTick > 1
                || data.lastMoveGapMs > 80) {
            data.timerSpeedSamples = 0;
            data.timerSpeedMeters = 0;
            return;
        }
        BlockPos below = player.blockPosition().below();
        if (isIce(player, below)) {
            data.timerSpeedSamples = 0;
            data.timerSpeedMeters = 0;
            return;
        }

        if (!player.onGround() || data.airTicks > 0) {
            return;
        }
        double step = Math.hypot(player.getX() - data.prevX, player.getZ() - data.prevZ);
        if (step < 0.08 || step >= 8.0) {
            return;
        }
        data.timerSpeedMeters += step;
        data.timerSpeedSamples++;
        if (data.timerSpeedSamples >= SPEED_SAMPLES) {
            if (data.timerSpeedMeters > MAX_VANILLA_METERS && data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Timer: %.2f blocks in %d ground ticks (vanilla sprint ~5.6, max %.1f)",
                                data.timerSpeedMeters, data.timerSpeedSamples, MAX_VANILLA_METERS));
            }
            data.timerSpeedSamples = 0;
            data.timerSpeedMeters = 0;
        }
    }

    public void onMovePacket(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().timerCheckEnabled) return;
        if (player.isDeadOrDying()) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        long now = System.currentTimeMillis();

        // Join, riding, and dismount all produce bursts / gaps in the packet
        // stream that have nothing to do with client timing (teleports,
        // vehicle control packets, respawn). Hold the rate detector until the
        // player settles; dismount settling is longer than the shared 1s
        // vehicle-exit grace, so it is checked here.
        boolean vehicleSettling = data.vehicleExitMs > 0
                && now - data.vehicleExitMs < 3000L;
        if (data.joinGraceTicks > 0 || vehicleSettling || player.isPassenger()) {
            data.timerRateStreak = 0;
            data.timerFastStreak = 0;
            return;
        }

        // data.timerPacketsThisTick is incremented by
        // ServerGamePacketListenerMixin for every position packet, so it is
        // available to SpeedCheck even when this check is disabled.
        data.movePacketTimestamps.addLast(now);
        while (data.movePacketTimestamps.size() > 200) {
            data.movePacketTimestamps.pollFirst();
        }

        // Rolling 1s packet-rate gate (configurable timerMaxPacketsPerSecond).
        // A single lag burst can exceed the cap, so three consecutive windows
        // above it are required before flagging.
        while (!data.movePacketTimestamps.isEmpty()
                && now - data.movePacketTimestamps.peekFirst() > RATE_WINDOW_MS) {
            data.movePacketTimestamps.pollFirst();
        }
        int maxRate = Praxic.getConfig().timerMaxPacketsPerSecond;
        if (data.movePacketTimestamps.size() > maxRate) {
            data.timerRateStreak++;
        } else {
            data.timerRateStreak = 0;
        }
        if (data.timerRateStreak >= RATE_STREAK && data.canFlag(getName() + "_rate", 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Timer: %d move packets in rolling 1s (max %d)",
                            data.movePacketTimestamps.size(), maxRate));
            data.timerRateStreak = 0;
        }
    }

    private static boolean isIce(ServerPlayer player, BlockPos below) {
        var b = player.level().getBlockState(below).getBlock();
        return b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b == Blocks.FROSTED_ICE;
    }
}

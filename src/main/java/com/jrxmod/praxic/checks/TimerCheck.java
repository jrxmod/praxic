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
 * 50 ms-per-packet balance.
 */
public class TimerCheck extends AbstractCheck {

    private static final int PACKET_COST_MS = 50;
    private static final int BALANCE_FLAG_MS = 500;
    private static final int BALANCE_MAX_MS = 2500;
    private static final int STALL_RESET_MS = 250;
    private static final int FAST_TICK_STREAK = 8;
    private static final int SPEED_SAMPLES = 20;
    /** Vanilla grounded sprint is ~5.6 m/s. Timer x2 walk is ~8.6, x2 sprint ~11. */
    private static final double MAX_VANILLA_METERS = 6.6;

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
            data.timerBalanceMs = 0;
            data.timerSpeedSamples = 0;
            data.timerSpeedMeters = 0;
            return;
        }

        if (data.timerPacketsThisTick >= 2) {
            data.timerFastStreak++;
        } else {
            data.timerFastStreak = Math.max(0, data.timerFastStreak - 1);
        }
        if (data.timerFastStreak >= FAST_TICK_STREAK && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Timer: %d ticks with 2+ move packets (n=%d)",
                            data.timerFastStreak, data.timerPacketsThisTick));
            data.timerFastStreak = 0;
            data.timerBalanceMs = 0;
        }
        data.timerPacketsThisTick = 0;

        if (player.isPassenger() || player.isFallFlying() || player.isAutoSpinAttack()
                || player.isInWater() || player.isInLava()
                || player.hurtTime > 0
                || data.joinGraceTicks > 0) {
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
        data.timerPacketsThisTick++;
        data.movePacketTimestamps.addLast(now);
        while (data.movePacketTimestamps.size() > 200) {
            data.movePacketTimestamps.pollFirst();
        }

        if (data.timerLastMs == 0L) {
            data.timerLastMs = now;
            return;
        }

        long real = now - data.timerLastMs;
        data.timerLastMs = now;
        if (real > STALL_RESET_MS) {
            data.timerBalanceMs = 0;
            return;
        }

        data.timerBalanceMs += PACKET_COST_MS - (int) real;
        if (data.timerBalanceMs < 0) data.timerBalanceMs = 0;
        if (data.timerBalanceMs > BALANCE_MAX_MS) data.timerBalanceMs = BALANCE_MAX_MS;

        if (data.timerBalanceMs >= BALANCE_FLAG_MS && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Timer: client ahead %dms", data.timerBalanceMs));
            data.timerBalanceMs = 0;
            data.timerFastStreak = 0;
        }
    }

    private static boolean isIce(ServerPlayer player, BlockPos below) {
        var b = player.level().getBlockState(below).getBlock();
        return b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b == Blocks.FROSTED_ICE;
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects FastPlace - placing blocks faster than vanilla allows.
 * Vanilla has 4 tick (200ms) rightClickDelay between placements.
 * Removing that delay allows placement every tick (50ms), which is 5x faster.
 */
public class FastPlaceCheck extends AbstractCheck {

    private static final long VANILLA_DELAY_MS = 200L;
    private static final long FAST_THRESHOLD_MS = 120L;
    private static final long WINDOW_MS = 1000L;
    private static final double BALANCE_LIMIT = 1000.0;

    @Override
    public String getName() {
        return "FastPlaceCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (System.currentTimeMillis() - data.lastPlaceTime > 2000L) {
            if (Math.abs(data.fastPlaceBalance) > 0.1) {
                data.fastPlaceBalance *= 0.95;
            }
        }
        long now = System.currentTimeMillis();
        while (!data.placeTimes.isEmpty() && now - data.placeTimes.peekFirst() > WINDOW_MS) {
            data.placeTimes.pollFirst();
        }
    }

    public void onBlockPlace(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastPlaceCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        long now = System.currentTimeMillis();

        data.placeTimes.addLast(now);
        while (!data.placeTimes.isEmpty() && now - data.placeTimes.peekFirst() > WINDOW_MS) {
            data.placeTimes.pollFirst();
        }

        if (data.lastPlaceTime > 0) {
            long delay = now - data.lastPlaceTime;
            if (delay < 150L) {
                data.fastPlaceBalance += VANILLA_DELAY_MS - delay;
            } else {
                data.fastPlaceBalance *= 0.9;
            }
            data.fastPlaceBalance = Math.max(-BALANCE_LIMIT, Math.min(BALANCE_LIMIT, data.fastPlaceBalance));

            if (data.fastPlaceBalance > 500.0 && data.canFlag(getName(), 1500)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastPlace delay: %dms (vanilla %dms) balance %.1f | rate %d/s | cooldown removed", delay, VANILLA_DELAY_MS, data.fastPlaceBalance, data.placeTimes.size()));
                data.fastPlaceBalance = 0;
            }

            if (delay < FAST_THRESHOLD_MS) {
                data.fastPlaceCount++;
                if (data.fastPlaceCount >= 5 && data.canFlag(getName(), 1000)) {
                    ViolationManager.flag(player, data, this,
                            String.format("FastPlace rapid: %d places < %dms apart | %d in last sec", data.fastPlaceCount, FAST_THRESHOLD_MS, data.placeTimes.size()));
                    data.fastPlaceCount = 0;
                }
            } else {
                data.fastPlaceCount = Math.max(0, data.fastPlaceCount - 1);
            }
        }

        int rate = data.placeTimes.size();
        int maxRate = Praxic.getConfig().fastPlaceMaxBlocksPerSecond > 0
                ? Praxic.getConfig().fastPlaceMaxBlocksPerSecond : 12;
        if (rate > maxRate && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("FastPlace rate: %d blocks/sec (max %d, vanilla ~5)", rate, maxRate));
            data.placeTimes.clear();
        } else if (rate > 8 && maxRate >= 20) {
            data.fastPlaceCount++;
            if (data.fastPlaceCount >= 8 && data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastPlace: %d blocks/sec exceeds vanilla (5/s)", rate));
                data.fastPlaceCount = 0;
            }
        }

        data.lastPlaceTime = now;
    }
}

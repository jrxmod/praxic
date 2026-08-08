package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects Tower hack — placing blocks directly under and moving straight up rapidly.
 * Legitimate tower: player jumps then places. Tower hack places without jumping or at excessive rate.
 */
public class TowerCheck extends AbstractCheck {

    // 1 second window
    private static final long WINDOW_MS = 1000L;
    private static final int MIN_BLOCKS_FOR_TOWER = 4;
    private static final double MIN_UP_MOVEMENT = 0.8;

    @Override
    public String getName() {
        return "TowerCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via onBlockPlace
    }

    public void onBlockPlace(ServerPlayer player, BlockPos placedPos, PlayerData data) {
        if (!Praxic.getConfig().towerCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.getAbilities().mayfly) return;
        if (data.joinGraceTicks > 0) return;

        int playerY = player.blockPosition().getY();
        // Only consider blocks at or directly below feet
        if (placedPos.getY() > playerY) return;
        if (placedPos.getY() < playerY - 1) return;

        double dy = player.getY() - data.prevY;
        // Tower requires upward movement
        if (dy < 0.05) {
            // Reset if not moving up
            long now = System.currentTimeMillis();
            if (now - data.towerWindowStart > WINDOW_MS) {
                data.towerBlockCount = 0;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - data.towerWindowStart > WINDOW_MS) {
            data.towerBlockCount = 0;
            data.towerWindowStart = now;
            data.towerLastY = player.getY();
        }

        // Must be vertical column: check if Y increasing
        double totalUp = player.getY() - data.towerLastY;
        data.towerBlockCount++;

        if (data.towerBlockCount >= MIN_BLOCKS_FOR_TOWER && totalUp >= MIN_UP_MOVEMENT) {
            double rate = data.towerBlockCount / ((now - data.towerWindowStart) / 1000.0);
            double maxRate = Praxic.getConfig().towerMaxBlocksPerSecond > 0 ? Praxic.getConfig().towerMaxBlocksPerSecond : 6.0;
            if (rate > maxRate && dy > 0.35) {
                if (data.canFlag(getName(), 2000)) {
                    ViolationManager.flag(player, data, this,
                            String.format("Tower: %d blocks in %.2fs rate=%.1f (max %.1f) dy=%.3f",
                                    data.towerBlockCount, (now - data.towerWindowStart)/1000.0, rate, maxRate, dy));
                }
                data.towerBlockCount = 0;
                data.towerWindowStart = now;
                data.towerLastY = player.getY();
            }
        }

        data.towerLastY = player.getY();
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects Tower - placing blocks directly under and moving straight up rapidly.
 * Legitimate tower: player jumps then places, about 1.5-2 blocks per second.
 * Modified clients place 6+ blocks per second without proper jump timing.
 * This version is very lenient to avoid flagging legit players holding RMB.
 */
public class TowerCheck extends AbstractCheck {

    private static final long WINDOW_MS = 1500L;
    private static final int MIN_BLOCKS_FOR_TOWER = 6;
    private static final double MIN_UP_MOVEMENT = 2.5;
    private static final double MAX_HORIZONTAL = 2.0;

    @Override
    public String getName() {
        return "TowerCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (System.currentTimeMillis() - data.towerWindowStart > 3000L) {
            data.towerBlockCount = 0;
        }
    }

    public void onBlockPlace(ServerPlayer player, BlockPos placedPos, PlayerData data) {
        if (!Praxic.getConfig().towerCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.getAbilities().mayfly) return;
        if (data.joinGraceTicks > 0) return;

        int playerY = player.blockPosition().getY();
        if (placedPos.getY() > playerY) return;
        if (placedPos.getY() < playerY - 2) return;
        double dx = Math.abs(placedPos.getX() - player.blockPosition().getX());
        double dz = Math.abs(placedPos.getZ() - player.blockPosition().getZ());
        if (dx > 1 || dz > 1) return;

        long now = System.currentTimeMillis();
        if (now - data.towerWindowStart > WINDOW_MS) {
            if (now - data.towerWindowStart > 3000L) {
                data.towerLastY = player.getY();
                data.towerStartX = player.getX();
                data.towerStartZ = player.getZ();
            }
            data.towerBlockCount = 0;
            data.towerWindowStart = now;
        }

        // Horizontal drift check - if player moved far horizontally, not a vertical tower
        double horizDist = Math.hypot(player.getX() - data.towerStartX, player.getZ() - data.towerStartZ);
        if (data.towerBlockCount == 0) {
            data.towerStartX = player.getX();
            data.towerStartZ = player.getZ();
            data.towerLastY = player.getY() - 0.2;
            horizDist = 0;
        }
        if (horizDist > MAX_HORIZONTAL) {
            // Player moved sideways, reset tower counter
            data.towerBlockCount = 0;
            data.towerWindowStart = now;
            data.towerStartX = player.getX();
            data.towerStartZ = player.getZ();
            data.towerLastY = player.getY();
            return;
        }

        data.towerBlockCount++;
        double totalUp = player.getY() - data.towerLastY;

        if (data.towerBlockCount < MIN_BLOCKS_FOR_TOWER) return;

        double elapsedSec = (now - data.towerWindowStart) / 1000.0;
        if (elapsedSec < 0.3) elapsedSec = 0.3;
        double rate = data.towerBlockCount / elapsedSec;

        // Enforce minimum 10 blocks per second regardless of config, to avoid false positives
        double configRate = Praxic.getConfig().towerMaxBlocksPerSecond;
        double maxRate = configRate > 0 ? Math.max(10.0, configRate) : 10.0;

        // Require significant upward movement proportional to blocks
        // For 6 blocks, need at least 2.5 up, for 8 blocks need 4 up, etc.
        double requiredUp = Math.max(MIN_UP_MOVEMENT, data.towerBlockCount * 0.6);

        if (rate > maxRate && totalUp >= requiredUp) {
            if (data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Tower: %d blocks in %.2fs rate=%.1f (max %.1f) up=%.2f horiz=%.2f",
                                data.towerBlockCount, elapsedSec, rate, maxRate, totalUp, horizDist));
            }
            data.towerBlockCount = 0;
            data.towerWindowStart = now;
            data.towerLastY = player.getY();
            data.towerStartX = player.getX();
            data.towerStartZ = player.getZ();
        } else if (rate > maxRate + 4.0 && data.towerBlockCount >= 8) {
            if (data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Tower fast: %d blocks in %.2fs rate=%.1f", data.towerBlockCount, elapsedSec, rate));
            }
            data.towerBlockCount = 0;
            data.towerWindowStart = now;
            data.towerLastY = player.getY();
            data.towerStartX = player.getX();
            data.towerStartZ = player.getZ();
        }
    }
}

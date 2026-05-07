package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class ScaffoldCheck extends AbstractCheck {

    // Detection window in ms
    private static final long WINDOW_MS = 1000;

    // Minimum horizontal movement to confirm player is walking while bridging
    private static final double MIN_MOVEMENT = 0.1;

    @Override
    public String getName() {
        return "ScaffoldCheck";
    }

    // Called from mixin on every block placement — runs on server thread
    public void onBlockPlace(ServerPlayer player, BlockPos placedPos, PlayerData data) {

        if (!Praxic.getConfig().scaffoldCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        // Only care about blocks placed at or below player's feet
        int playerY = player.blockPosition().getY();
        if (placedPos.getY() > playerY) return;
        if (placedPos.getY() < playerY - 1) return;

        // Only flag if player is actually moving horizontally
        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double movement = Math.sqrt(dx * dx + dz * dz);
        if (movement < MIN_MOVEMENT) return;

        long now = System.currentTimeMillis();

        // Reset window if expired
        if (now - data.scaffoldWindowStart > WINDOW_MS) {
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
        }

        data.scaffoldBlocksPlaced++;

        int maxBlocks = Praxic.getConfig().scaffoldMaxBlocksPerSecond;

        if (data.scaffoldBlocksPlaced >= maxBlocks && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Scaffold: %d blocks/sec under feet (max: %d)",
                            data.scaffoldBlocksPlaced, maxBlocks));
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
        }
    }

    // Not used — detection is fully event-driven via onBlockPlace
    @Override
    public void check(ServerPlayer player, PlayerData data) {}
}

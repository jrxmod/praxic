package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Scaffold cheats send a rotation-only look packet, then right-click.
 * Vanilla walking sends PosRot (position+look together), never a bare Rot
 * immediately before a place. Rate-while-moving is vanilla bridging and FPs.
 */
public class ScaffoldCheck extends AbstractCheck {

    private static final long WINDOW_MS = 2000;
    private static final long LOOK_SPOOF_MS = 250;
    private static final double MIN_WINDOW_MOVE = 1.2;
    private static final int PLACE_THRESHOLD = 3;

    @Override
    public String getName() {
        return "ScaffoldCheck";
    }

    public void onBlockPlace(ServerPlayer player, BlockPos placedPos, PlayerData data) {
        if (!Praxic.getConfig().scaffoldCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        int playerY = player.blockPosition().getY();
        if (placedPos.getY() > playerY - 1) return;
        if (placedPos.getY() < playerY - 2) return;

        int dxb = Math.abs(placedPos.getX() - player.blockPosition().getX());
        int dzb = Math.abs(placedPos.getZ() - player.blockPosition().getZ());
        if (dxb > 1 || dzb > 1) return;

        long now = System.currentTimeMillis();
        if (now - data.lastLookOnlyMs > LOOK_SPOOF_MS) return;

        if (data.scaffoldBlocksPlaced == 0 || now - data.scaffoldWindowStart > WINDOW_MS) {
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
            data.scaffoldStartX = player.getX();
            data.scaffoldStartZ = player.getZ();
        }

        data.scaffoldBlocksPlaced++;

        double moved = Math.hypot(player.getX() - data.scaffoldStartX,
                player.getZ() - data.scaffoldStartZ);
        if (data.scaffoldBlocksPlaced >= PLACE_THRESHOLD
                && moved >= MIN_WINDOW_MOVE
                && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("ScaffoldWalk: %d look-spoof under-foot places in %dms moved=%.2f",
                            data.scaffoldBlocksPlaced, now - data.scaffoldWindowStart, moved));
            data.scaffoldBlocksPlaced = 0;
            data.scaffoldWindowStart = now;
            data.scaffoldStartX = player.getX();
            data.scaffoldStartZ = player.getZ();
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {}
}

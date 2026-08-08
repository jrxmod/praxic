package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects FastPlace — placing blocks faster than the vanilla limit.
 * Vanilla processes at most one placement per game tick, so 20 blocks/sec
 * is the hard ceiling; sustained rates above it indicate packet spam.
 * Only successful block placements are counted (see ServerPlayerGameModeMixin).
 */
public class FastPlaceCheck extends AbstractCheck {

    private static final long WINDOW_MS = 1000L;

    @Override
    public String getName() {
        return "FastPlaceCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven
    }

    public void onBlockPlace(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastPlaceCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        long now = System.currentTimeMillis();
        if (now - data.fastPlaceWindowStart > WINDOW_MS) {
            data.fastPlaceCount = 0;
            data.fastPlaceWindowStart = now;
        }

        data.fastPlaceCount++;

        int max = Praxic.getConfig().fastPlaceMaxBlocksPerSecond > 0
                ? Praxic.getConfig().fastPlaceMaxBlocksPerSecond : 20;

        if (data.fastPlaceCount > max && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("FastPlace: %d blocks/sec (max %d)", data.fastPlaceCount, max));
            data.fastPlaceCount = 0;
            data.fastPlaceWindowStart = now;
        }
    }
}

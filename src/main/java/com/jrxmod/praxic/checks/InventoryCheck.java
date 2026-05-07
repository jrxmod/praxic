package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class InventoryCheck extends AbstractCheck {

    // Detection window in ms
    private static final long WINDOW_MS = 1000;

    @Override
    public String getName() {
        return "InventoryCheck";
    }

    // Called from mixin on every container click — runs on server thread
    public void onInventoryClick(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().inventoryCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        long now = System.currentTimeMillis();

        // Reset window if expired
        if (now - data.inventoryWindowStart > WINDOW_MS) {
            data.inventoryClickCount = 0;
            data.inventoryWindowStart = now;
        }

        data.inventoryClickCount++;

        int maxClicks = Praxic.getConfig().inventoryMaxClicksPerSecond;

        if (data.inventoryClickCount >= maxClicks && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("InventoryCheck: %d clicks/sec (max: %d)",
                            data.inventoryClickCount, maxClicks));
            data.inventoryClickCount = 0;
            data.inventoryWindowStart = now;
        }
    }

    // Not used — detection is fully event-driven via onInventoryClick
    @Override
    public void check(ServerPlayer player, PlayerData data) {}
}

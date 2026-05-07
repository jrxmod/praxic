package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class AutoTotemCheck extends AbstractCheck {

    // Below this threshold re-equip is physically impossible
    private static final long MIN_REEQUIP_MS = 150;

    // Reset totem tracking if player never re-equipped within this window
    private static final long TOTEM_TRACKING_TIMEOUT_MS = 2000;

    @Override
    public String getName() {
        return "AutoTotemCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().autoTotemCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        boolean hasTotem = hasTotemInHand(player);
        long now = System.currentTimeMillis();

        // Totem disappeared from hand while player is alive — totem was consumed
        if (data.hadTotemInHand && !hasTotem) {
            data.lastTotemUseTime = now;
        }

        // Totem reappeared after being consumed — measure how fast
        if (!data.hadTotemInHand && hasTotem && data.lastTotemUseTime > 0) {
            long delta = now - data.lastTotemUseTime;
            if (delta < MIN_REEQUIP_MS && data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("AutoTotem: re-equipped in %dms (min: %dms)", delta, MIN_REEQUIP_MS));
            }
            // Reset regardless — one totem use cycle complete
            data.lastTotemUseTime = 0;
        }

        // Timeout — player never re-equipped, stop tracking
        if (data.lastTotemUseTime > 0 && now - data.lastTotemUseTime > TOTEM_TRACKING_TIMEOUT_MS) {
            data.lastTotemUseTime = 0;
        }

        data.hadTotemInHand = hasTotem;
    }

    private boolean hasTotemInHand(ServerPlayer player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }
}

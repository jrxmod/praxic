package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.level.GameType;

/**
 * Detects Wind Charge abuse.
 *
 * Vanilla cooldown is 10 ticks (0.5 s). A use packet while the item is already
 * on cooldown is cancelled when mitigation is on. A burst of charges used as
 * fly fuel (several uses while hovering) is flagged on the tick pipeline.
 */
public class WindChargeAbuseCheck extends AbstractCheck {

    private static final long WINDOW_MS = 2000L;
    private static final int MAX_USES_IN_WINDOW = 3;
    private static final int HOVER_AIR_TICKS = 20;

    @Override
    public String getName() {
        return "WindChargeAbuseCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().windChargeAbuseCheckEnabled) return;
        if (player.isSpectator() || player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        long now = System.currentTimeMillis();
        while (!data.windChargeUseTimes.isEmpty()
                && now - data.windChargeUseTimes.peekFirst() > WINDOW_MS) {
            data.windChargeUseTimes.pollFirst();
        }

        if (data.airTicks < HOVER_AIR_TICKS) return;
        double dy = player.getY() - data.prevY;
        if (dy < -0.05) return;
        if (data.windChargeUseTimes.size() < MAX_USES_IN_WINDOW) return;
        if (!data.canFlag(getName() + "_fuel", 3000)) return;

        ViolationManager.flag(player, data, this,
                String.format("Wind charge fly-fuel: %d uses in 2s while hovering airTicks=%d",
                        data.windChargeUseTimes.size(), data.airTicks));
    }

    /**
     * @return true if the use packet should be cancelled
     */
    public boolean onUse(ServerPlayer player, PlayerData data, Item item) {
        if (!Praxic.getConfig().windChargeAbuseCheckEnabled) return false;
        if (!(item instanceof WindChargeItem)) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (data.joinGraceTicks > 0) return false;
        if (Praxic.getWhitelistManager() != null
                && Praxic.getWhitelistManager().isWhitelisted(player.getUUID())) return false;

        if (player.getCooldowns().isOnCooldown(item)) {
            if (data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this, "Wind charge used during vanilla cooldown");
            }
            return Praxic.getConfig().enableMitigation;
        }

        if (!player.onGround()) {
            data.windChargeUseTimes.addLast(System.currentTimeMillis());
        }
        return false;
    }
}

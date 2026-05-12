package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;

public class AutoClickerCheck extends AbstractCheck {

    private static final long WINDOW_MS = 1000L;

    @Override
    public String getName() {
        return "AutoClickerCheck";
    }

    // Tick-based check not used — this check is fully event-driven via onAttack()
    @Override
    public void check(ServerPlayer player, PlayerData data) {}

    public void onAttack(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().autoClickerCheckEnabled) return;
        if (player.isDeadOrDying()) return;

        long now = System.currentTimeMillis();

        // Add current attack timestamp to sliding window
        data.attackTimestamps.addLast(now);

        // Remove timestamps outside the 1-second window
        while (!data.attackTimestamps.isEmpty() &&
               now - data.attackTimestamps.peekFirst() > WINDOW_MS) {
            data.attackTimestamps.pollFirst();
        }

        int cps = data.attackTimestamps.size();
        int maxCps = Praxic.getConfig().autoClickerMaxCps;

        if (cps > maxCps) {
            ViolationManager.flag(player, data, this,
                    "CPS: " + cps + " (max: " + maxCps + ")");
        }
    }
}

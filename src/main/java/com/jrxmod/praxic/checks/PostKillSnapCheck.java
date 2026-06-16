package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.PlayerAnalytics;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects Kill Aura via post-kill snap angle.
 *
 * After killing an entity, Kill Aura immediately snaps to the next target
 * (often 90-180 degrees). Humans typically snap 10-30 degrees or less.
 *
 * Data is collected by RotationAnalyzer.onKill() — this check only reads
 * the result from RotationProfile.postKillSnapAngle.
 */
public class PostKillSnapCheck extends AbstractCheck {

    @Override
    public String getName() {
        return "PostKillSnapCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().postKillSnapCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;

        PlayerAnalytics analytics = Praxic.getCheckManager().getAnalytics(player.getUUID());
        if (analytics == null) return;

        // -1.0 means outside post-kill window — nothing to check
        double snapAngle = analytics.rotation.postKillSnapAngle;
        if (snapAngle < 0.0) return;

        double threshold = Praxic.getConfig().postKillSnapMaxAngle;
        if (snapAngle >= threshold && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Post-kill snap %.1f deg (max: %.1f deg)",
                            snapAngle, threshold));
        }
    }
}

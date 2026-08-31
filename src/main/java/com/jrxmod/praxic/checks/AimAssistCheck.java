package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.RotationProfile;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects AimAssist from abnormally low yaw-entropy during combat.
 *
 * RotationAnalyzer already computes Shannon entropy over a 40-tick window.
 * Human aim is noisy (roughly 3.5-4.0 bits). Aim-assist modules produce a
 * much smoother curve (roughly 1.0-2.0 bits) while still rotating toward a
 * target. Standing still is excluded by requiring a minimum rotation speed.
 */
public class AimAssistCheck extends AbstractCheck {

    private static final long COMBAT_WINDOW_MS = 2500L;
    private static final int MIN_SAMPLES = 40;
    private static final double MAX_ENTROPY = 2.15;
    private static final double MIN_ROTATION_SPEED = 2.5;
    private static final int BUFFER_THRESHOLD = 10;

    @Override
    public String getName() {
        return "AimAssistCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().aimAssistCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        boolean inCombat = (System.currentTimeMillis() - data.lastAttackTime) < COMBAT_WINDOW_MS;
        if (!inCombat) {
            data.aimAssistBuffer = Math.max(0, data.aimAssistBuffer - 1);
            return;
        }

        RotationProfile rot = Praxic.getCheckManager().getRotationProfile(player.getUUID());
        if (rot == null || rot.sampleCount < MIN_SAMPLES || rot.entropy < 0.0) {
            return;
        }

        boolean suspicious = rot.entropy <= MAX_ENTROPY
                && rot.avgRotationSpeed >= MIN_ROTATION_SPEED;
        if (suspicious) {
            data.aimAssistBuffer++;
        } else {
            data.aimAssistBuffer = Math.max(0, data.aimAssistBuffer - 1);
        }

        if (data.aimAssistBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Combat entropy %.2f bits (max %.2f) rotSpeed %.2f buffer %d",
                            rot.entropy, MAX_ENTROPY, rot.avgRotationSpeed, data.aimAssistBuffer));
            data.aimAssistBuffer = 0;
        }
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.RotationProfile;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects AimAssist from machine-regular rotation during combat.
 *
 * Assisted aim applies a constant per-tick rotation step toward the target,
 * so the per-tick rotation speed stays nearly constant and the coefficient
 * of variation is very low. Hand mouse input is bursty: speed varies a lot
 * even while smoothly following a target, which is why the previous entropy
 * heuristic flagged legit players who trace an entity. A >320deg yaw delta in
 * one tick (a modulo-360 wrap artifact) is flagged independently because it
 * has no legit equivalent.
 */
public class AimAssistCheck extends AbstractCheck {

    private static final long COMBAT_WINDOW_MS = 2500L;
    private static final int MIN_SAMPLES = 20;
    private static final double MIN_ROTATION_SPEED = 1.0;
    private static final double MAX_ROTATION_SPEED = 120.0;
    private static final double MAX_SPEED_CV = 0.18;
    private static final int BUFFER_THRESHOLD = 10;
    private static final int SNAP_THRESHOLD = 1;

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
        if (rot == null || rot.sampleCount < MIN_SAMPLES) {
            return;
        }

        // Constant-rate assisted rotation: active speed within the typical
        // assist range and a near-zero coefficient of variation.
        boolean constantRate = rot.avgRotationSpeed >= MIN_ROTATION_SPEED
                && rot.avgRotationSpeed <= MAX_ROTATION_SPEED
                && rot.rotationSpeedCV >= 0.0
                && rot.rotationSpeedCV <= MAX_SPEED_CV;
        if (constantRate) {
            data.aimAssistBuffer++;
        } else {
            data.aimAssistBuffer = Math.max(0, data.aimAssistBuffer - 1);
        }

        if (data.aimAssistBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Constant-rate combat rotation speed %.2f deg/tick, CV %.3f, buffer %d",
                            rot.avgRotationSpeed, rot.rotationSpeedCV, data.aimAssistBuffer));
            data.aimAssistBuffer = 0;
        }

        // Modulo-360 wrap artifact: unwrapped 320deg+ yaw step in one tick.
        if (rot.largeSnapCount >= SNAP_THRESHOLD && data.canFlag(getName() + "_snap", 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Large yaw snap %.1f deg/tick (modulo-360 artifact)",
                            rot.maxSnapAngle));
        }
    }
}

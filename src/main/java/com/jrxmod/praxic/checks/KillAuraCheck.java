package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class KillAuraCheck extends AbstractCheck {

    // KillAura typically hits entities fully behind the player (>100 degrees)
    private static final double MAX_ATTACK_ANGLE = 100.0;

    // Time window for burst detection (ms)
    private static final long BURST_WINDOW_MS = 1000;

    // Max attacks per second — jitter clicking ~15 cps, autoclicker 20+
    private static final int MAX_ATTACKS_PER_SECOND = 18;

    @Override
    public String getName() {
        return "KillAuraCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven check — called from ServerGamePacketListenerMixin
    }

    public void checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {

        if (!Praxic.getConfig().killAuraCheckEnabled) return;

        if (attacker.isSpectator()) return;
        if (attacker.isDeadOrDying()) return;
        if (attacker.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        checkAngle(attacker, target, data);
        checkBurst(attacker, data);
    }

    // Detects hitting entities without looking at them
    private void checkAngle(ServerPlayer attacker, Entity target, PlayerData data) {

        Vec3 lookDir = attacker.getLookAngle().normalize();
        Vec3 toTarget = target.position().subtract(attacker.getEyePosition()).normalize();

        double dot = lookDir.dot(toTarget);
        dot = Math.min(1.0, Math.max(-1.0, dot));
        double angle = Math.toDegrees(Math.acos(dot));

        if (angle > MAX_ATTACK_ANGLE && data.canFlag(getName() + "_angle", 2000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack angle: %.1f degrees (max: %.1f)", angle, MAX_ATTACK_ANGLE));
        }
    }

    // Detects abnormal attack burst — autoclicker/killaura typically 20+ cps
    private void checkBurst(ServerPlayer attacker, PlayerData data) {

        long now = System.currentTimeMillis();

        if (now - data.rapidAttackWindowStart > BURST_WINDOW_MS) {
            data.rapidAttackCount = 0;
            data.rapidAttackWindowStart = now;
        }

        data.rapidAttackCount++;

        if (data.rapidAttackCount > MAX_ATTACKS_PER_SECOND && data.canFlag(getName() + "_burst", 3000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Attack burst: %d hits/sec (max: %d)",
                            data.rapidAttackCount, MAX_ATTACKS_PER_SECOND));
        }
    }
}

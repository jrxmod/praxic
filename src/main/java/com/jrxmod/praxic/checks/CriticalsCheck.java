package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * Detects spoofed critical hits.
 *
 * Legitimate critical hits happen while the player is actually falling after a
 * jump. Criticals modules spoof tiny airborne packets around the attack so the
 * server accepts a crit even though the movement state is still ground-like.
 */
public class CriticalsCheck extends AbstractCheck {

    private static final int BUFFER_THRESHOLD = 2;
    private static final double FLAT_VERTICAL_DELTA = 0.006;

    @Override
    public String getName() {
        return "CriticalsCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via attack packet handling.
    }

    public void checkAttack(ServerPlayer attacker, Entity target, PlayerData data) {
        if (!Praxic.getConfig().criticalsCheckEnabled) return;
        if (attacker.isSpectator()) return;
        if (attacker.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (attacker.isDeadOrDying()) return;
        if (attacker.getAbilities().mayfly) return;
        if (attacker.isPassenger()) return;
        if (attacker.isFallFlying()) return;
        if (attacker.isInWater() || attacker.isInLava()) return;
        if (attacker.onClimbable()) return;
        if (attacker.hasEffect(MobEffects.BLINDNESS)) return;
        if (data.joinGraceTicks > 0) return;

        // If the server is not in critical-hit conditions, decay buffer only.
        boolean inCriticalWindow = !attacker.onGround() && attacker.fallDistance > 0.0f;
        if (!inCriticalWindow) {
            data.criticalsBuffer = Math.max(0, data.criticalsBuffer - 1);
            return;
        }

        double dy = attacker.getY() - data.prevY;

        // Signature of the packet-spoof: the position returns to its pre-attack
        // value (flat delta) while the server still credits a falling state.
        // A genuine crit after stepping off a block moves down by roughly
        // gravity per tick, so its delta is not flat.
        boolean flatSpoof = Math.abs(dy) <= FLAT_VERTICAL_DELTA
                && attacker.fallDistance < 0.12f;

        if (flatSpoof) {
            data.criticalsBuffer++;
        } else {
            data.criticalsBuffer = Math.max(0, data.criticalsBuffer - 1);
        }

        if (data.criticalsBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(attacker, data, this,
                    String.format("Spoofed critical: fall=%.3f dy=%.4f target=%s",
                            attacker.fallDistance,
                            dy,
                            target.getName().getString()));
            data.criticalsBuffer = 0;
        }
    }
}

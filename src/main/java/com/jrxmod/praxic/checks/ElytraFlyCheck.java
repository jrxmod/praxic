package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * Detects ElytraFly modules.
 * Vanilla elytra gliding follows predictable physics: gravity + drag.
 * ElytraFly keeps horizontal speed high and vertical descent low or even ascending without rockets.
 */
public class ElytraFlyCheck extends AbstractCheck {

    private static final int MIN_FLYING_TICKS = 20;
    private static final int BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECAY = 1;
    private static final double MAX_HORIZONTAL_SPEED = 2.5;
    private static final double MIN_FALL_SPEED_FOR_GLIDE = -0.5;

    @Override
    public String getName() {
        return "ElytraFlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().elytraFlyCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        if (!player.isFallFlying()) {
            data.elytraBuffer = Math.max(0, data.elytraBuffer - BUFFER_DECAY);
            return;
        }

        if (data.elytraAirTicks < MIN_FLYING_TICKS) return;

        if (player.hasEffect(MobEffects.SLOW_FALLING) || player.hasEffect(MobEffects.LEVITATION)) {
            data.elytraBuffer = 0;
            return;
        }

        // Skip if using firework rocket recently (boost allowed)
        // Check if player has active fireworks boost is non-trivial without tracking;
        // we allow short periods of high speed as grace and require sustained buffer.
        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = player.getY() - data.prevY;

        int ping = player.connection.latency();
        double maxHoriz = MAX_HORIZONTAL_SPEED + LagCompensation.extraSpeed(ping) * 2.0;

        boolean suspicious = false;
        String reason = "";

        // Horizontal too high while gliding flat or ascending
        if (horizontal > maxHoriz && dy > MIN_FALL_SPEED_FOR_GLIDE) {
            suspicious = true;
            reason = String.format("Elytra flat high speed h=%.3f max=%.2f dy=%.3f", horizontal, maxHoriz, dy);
        }

        // Ascending without rockets: vanilla elytra can gain altitude only with rockets or strong updraft
        // Sustained upward movement while fall-flying without rockets is suspicious
        if (dy > 0.35 && horizontal > 1.0) {
            suspicious = true;
            reason = String.format("Elytra ascending too fast dy=%.3f h=%.3f", dy, horizontal);
        }

        if (suspicious) {
            data.elytraBuffer++;
        } else {
            data.elytraBuffer = Math.max(0, data.elytraBuffer - BUFFER_DECAY);
        }

        if (data.elytraBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this, reason + String.format(" (buffer %d/%d ping %dms)", data.elytraBuffer, BUFFER_THRESHOLD, ping));
            data.elytraBuffer = 0;
        }
    }
}

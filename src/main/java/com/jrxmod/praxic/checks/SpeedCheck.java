package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public class SpeedCheck extends AbstractCheck {

    // Skip check if tick took longer than this (server lag protection)
    private static final long MAX_TICK_DELTA_MS = 100;

    // Skip check if distance is impossibly large — likely a teleport
    private static final double TELEPORT_THRESHOLD = 8.0;

    // Number of consecutive ticks exceeding speed before flagging
    private static final int CONSECUTIVE_REQUIRED = 3;

    @Override
    public String getName() {
        return "SpeedCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().speedCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;

        // Skip on server lag
        long now = System.currentTimeMillis();
        long delta = now - data.lastPositionUpdate;
        if (delta > MAX_TICK_DELTA_MS) {
            data.speedConsecutive = 0;
            return;
        }

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double distancePerTick = Math.sqrt(dx * dx + dz * dz);

        // Skip teleports
        if (distancePerTick > TELEPORT_THRESHOLD) {
            data.speedConsecutive = 0;
            return;
        }

        double maxSpeed = Praxic.getConfig().speedMaxBlocksPerTick;

        // Adjust threshold for speed effect
        if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            int amplifier = player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
            maxSpeed += (amplifier + 1) * 0.2;
        }

        if (distancePerTick > maxSpeed) {
            data.speedConsecutive++;
            // Only flag after N consecutive ticks exceeding speed
            if (data.speedConsecutive >= CONSECUTIVE_REQUIRED && data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Speed: %.3f blocks/tick (max: %.3f) [%d consecutive]",
                                distancePerTick, maxSpeed, data.speedConsecutive));
            }
        } else {
            // Reset consecutive counter when speed is normal
            data.speedConsecutive = 0;
        }
    }
}

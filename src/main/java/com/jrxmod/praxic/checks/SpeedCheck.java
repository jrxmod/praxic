package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public class SpeedCheck extends AbstractCheck {

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

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double distancePerTick = Math.sqrt(dx * dx + dz * dz);

        double maxSpeed = Praxic.getConfig().speedMaxBlocksPerTick;

        if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            int amplifier = player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
            maxSpeed += (amplifier + 1) * 0.2;
        }

        if (distancePerTick > maxSpeed && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Speed: %.3f blocks/tick (max: %.3f)", distancePerTick, maxSpeed));
        }
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public class FlyCheck extends AbstractCheck {

    @Override
    public String getName() {
        return "FlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().flyCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;

        if (player.isInWater() || player.isInLava()) {
            data.airTicks    = 0;
            data.wasOnGround = true;
            return;
        }

        if (player.hasEffect(MobEffects.LEVITATION)) return;
        if (player.isFallFlying()) return;
        if (player.getAbilities().mayfly) return;

        if (player.onClimbable()) {
            data.airTicks    = 0;
            data.wasOnGround = true;
            return;
        }

        if (!player.onGround()) {
            data.airTicks++;
        } else {
            data.airTicks    = 0;
            data.wasOnGround = true;
            return;
        }

        int ping = player.connection.latency();

        // Scale air tick threshold with player latency
        int maxAirTicks = Praxic.getConfig().flyMaxAirTicks
                + LagCompensation.extraAirTicks(ping);

        // Flag sustained hovering / flying — vertical ascent is handled by YPredictionCheck
        if (data.airTicks > maxAirTicks && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Suspended in air for %d ticks (max: %d, ping: %dms)",
                            data.airTicks, maxAirTicks, ping));
        }

        data.wasOnGround = false;
    }
}

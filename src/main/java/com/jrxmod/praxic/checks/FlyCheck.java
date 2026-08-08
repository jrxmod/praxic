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
        if (player.getAbilities().mayfly) return;
        if (player.isPassenger()) return;
        if (player.isFallFlying()) return;
        if (player.hasEffect(MobEffects.LEVITATION)) return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;
        // Jump Boost changes jump arcs and fall damage — not a fly indicator
        if (player.hasEffect(MobEffects.JUMP)) return;

        // Grace period after leaving water — state machine already counted airTicks,
        // but the transition can look like hovering for a few ticks
        if (data.waterExitTicks > 0) return;

        int ping = player.connection.latency();

        // Scale air tick threshold with player latency
        int maxAirTicks = Praxic.getConfig().flyMaxAirTicks
                + LagCompensation.extraAirTicks(ping);

        // Natural falls accelerate past -0.05 blocks/tick within two ticks and are
        // not flying — exempt them so long cliff falls never flag.
        double dy = player.getY() - data.prevY;

        // Flag sustained hovering / flying — vertical ascent is handled by YPredictionCheck
        if (data.airTicks > maxAirTicks && dy >= -0.05 && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Suspended in air for %d ticks (max: %d, ping: %dms)",
                            data.airTicks, maxAirTicks, ping));
        }
    }
}

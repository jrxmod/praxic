package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
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
        if (player.hasEffect(MobEffects.LEVITATION)) return;
        if (player.isFallFlying()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.getAbilities().mayfly || player.getAbilities().flying) return;

        if (player.onGround()) {
            data.airTicks = 0;
        } else {
            data.airTicks++;

            int maxAirTicks = Praxic.getConfig().flyMaxAirTicks;

            if (data.airTicks >= maxAirTicks && data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        "Air ticks: " + data.airTicks);
            }
        }
    }
}

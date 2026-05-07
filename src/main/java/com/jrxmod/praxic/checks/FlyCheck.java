package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public class FlyCheck extends AbstractCheck {

    // Number of ticks to ignore vertical ascent after leaving water
    private static final int WATER_EXIT_GRACE_TICKS = 10;

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

        boolean inWater = player.isInWater();

        // Track water exit grace period
        if (data.wasInWater && !inWater) {
            data.waterExitTicks = WATER_EXIT_GRACE_TICKS;
        }
        data.wasInWater = inWater;

        if (data.waterExitTicks > 0) {
            data.waterExitTicks--;
        }

        if (inWater || player.isInLava()) {
            data.airTicks = 0;
            data.wasOnGround = true;
            return;
        }

        if (player.hasEffect(MobEffects.LEVITATION)) return;
        if (player.isFallFlying()) return; // Elytra
        if (player.getAbilities().mayfly) return;

        if (player.onClimbable()) {
            data.airTicks = 0;
            data.wasOnGround = true;
            return;
        }

        boolean onGround = player.onGround();
        double dy = player.getY() - data.prevY;

        // Hover / air time detection
        if (!onGround) {
            data.airTicks++;
        } else {
            data.airTicks = 0;
        }

        if (data.airTicks > Praxic.getConfig().flyMaxAirTicks && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Suspended in air for %d ticks", data.airTicks));
        }

        // Vertical ascent detection
        // Ignore shortly after leaving water to avoid false positives on water->land collision boosts
        if (data.waterExitTicks == 0) {
            if (!onGround && !data.wasOnGround && dy > 0 && player.hurtTime == 0) {
                if (data.airTicks > 15 && dy > 0.15 && data.canFlag(getName(), 2000)) {
                    ViolationManager.flag(player, data, this,
                            String.format("Illegal vertical ascent: dy=%.3f", dy));
                }
            }
        }

        data.wasOnGround = onGround;
    }
}

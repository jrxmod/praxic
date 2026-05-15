package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class VelocityCheck extends AbstractCheck {

    // Ticks to wait after hit before measuring displacement
    private static final int KNOCKBACK_CHECK_DELAY = 5;

    // Minimum horizontal displacement expected after knockback
    // Vanilla knockback moves player at least 0.35 blocks horizontally
    // Threshold is conservative to avoid flagging near walls or low-damage hits
    private static final double MIN_KNOCKBACK_DISPLACEMENT = 0.15;

    @Override
    public String getName() {
        return "VelocityCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().velocityCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;

        // Skip players in water — knockback is reduced/absorbed by fluid
        if (player.isInWater() || player.isInLava()) {
            data.knockbackPending = false;
            data.prevHurtTime = player.hurtTime;
            return;
        }

        // Detect fresh hit: hurtTime just reset to max (player was not hurt last tick)
        if (data.prevHurtTime == 0 && player.hurtTime > 0) {
            data.knockbackPending = true;
            data.knockbackStartX = player.getX();
            data.knockbackStartZ = player.getZ();
            data.knockbackTicksWaited = 0;
        }

        data.prevHurtTime = player.hurtTime;

        if (!data.knockbackPending) return;

        data.knockbackTicksWaited++;

        // Not enough ticks elapsed yet — keep waiting
        if (data.knockbackTicksWaited < KNOCKBACK_CHECK_DELAY) return;

        // Measure horizontal displacement since the hit
        double dx = player.getX() - data.knockbackStartX;
        double dz = player.getZ() - data.knockbackStartZ;
        double displacement = Math.sqrt(dx * dx + dz * dz);

        data.knockbackPending = false;

        if (displacement < MIN_KNOCKBACK_DISPLACEMENT && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Ignored knockback: displacement=%.3f (min: %.2f)",
                            displacement, MIN_KNOCKBACK_DISPLACEMENT));
        }
    }
}

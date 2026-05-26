package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class VelocityCheck extends AbstractCheck {

    // Ticks to wait after hit before measuring displacement
    private static final int KNOCKBACK_CHECK_DELAY = 5;

    // Minimum horizontal displacement expected after knockback
    private static final double MIN_KNOCKBACK_DISPLACEMENT = 0.05;

    @Override
    public String getName() {
        return "VelocityCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().velocityCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isPassenger()) return;

        // Reset pending check on death — player teleports to spawn within 5 ticks,
        // displacement measurement would compare pre-death and post-respawn positions
        if (player.getHealth() <= 0) {
            data.knockbackPending = false;
            data.prevHurtTime = player.hurtTime;
            return;
        }

        // Skip players in water — knockback is absorbed by fluid
        if (player.isInWater() || player.isInLava()) {
            data.knockbackPending = false;
            data.prevHurtTime = player.hurtTime;
            return;
        }

        // Detect fresh hit: hurtTime just reset to max
        if (data.prevHurtTime == 0 && player.hurtTime > 0) {

            // Skip fall damage — player is on ground or was falling last tick.
            // Fall damage triggers hurtTime but causes no horizontal knockback.
            boolean isFallDamage = player.onGround()
                    || data.prevMovementState == MovementState.FALLING
                    || data.movementState     == MovementState.GROUND;

            if (!isFallDamage) {
                data.knockbackPending     = true;
                data.knockbackStartX      = player.getX();
                data.knockbackStartZ      = player.getZ();
                data.knockbackTicksWaited = 0;
            }
        }

        data.prevHurtTime = player.hurtTime;

        if (!data.knockbackPending) return;

        data.knockbackTicksWaited++;
        if (data.knockbackTicksWaited < KNOCKBACK_CHECK_DELAY) return;

        // Player landed before measurement window ended — ground absorbed horizontal
        // movement, displacement will always be near zero: not a valid sample
        if (data.movementState == MovementState.GROUND) {
            data.knockbackPending = false;
            return;
        }

        double dx           = player.getX() - data.knockbackStartX;
        double dz           = player.getZ() - data.knockbackStartZ;
        double displacement = Math.sqrt(dx * dx + dz * dz);

        data.knockbackPending = false;

        if (displacement < MIN_KNOCKBACK_DISPLACEMENT && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Ignored knockback: displacement=%.3f (min: %.2f)",
                            displacement, MIN_KNOCKBACK_DISPLACEMENT));
        }
    }
}

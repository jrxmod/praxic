package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects sustained noclip / phase behaviour.
 *
 * A single in-wall tick can happen because of pistons, doors, chunk loading or
 * server corrections. PRAXIC only flags when the player remains inside a wall
 * while still moving horizontally for multiple consecutive ticks.
 */
public class PhaseCheck extends AbstractCheck {

    private static final double TELEPORT_THRESHOLD = 6.0;

    @Override
    public String getName() {
        return "PhaseCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().phaseCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.getAbilities().mayfly) return;
        if (player.isPassenger()) return;
        if (data.joinGraceTicks > 0) return;

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Teleports and severe lag can temporarily put a player in a wall.
        if (horizontal > TELEPORT_THRESHOLD) {
            data.phaseTicks = 0;
            return;
        }

        if (!player.isInWall() || horizontal < Praxic.getConfig().phaseMinHorizontalMove) {
            data.phaseTicks = 0;
            return;
        }

        data.phaseTicks++;
        int maxTicks = Math.max(1, Praxic.getConfig().phaseMaxTicksInBlock);
        if (data.phaseTicks >= maxTicks && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Inside solid collision for %d ticks while moving %.3f b/t",
                            data.phaseTicks, horizontal));
            data.phaseTicks = 0;
        }
    }
}

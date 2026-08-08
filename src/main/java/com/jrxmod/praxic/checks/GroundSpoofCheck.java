package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects GroundSpoof — client claims onGround=true while server sees airborne for sustained period.
 * Used by many fly / nofall modules to avoid fall damage and maintain speed.
 */
public class GroundSpoofCheck extends AbstractCheck {

    private static final int MIN_AIR_TICKS = 10;
    private static final int SPOOF_TICKS_THRESHOLD = 15;
    private static final int BUFFER_THRESHOLD = 2;

    @Override
    public String getName() {
        return "GroundSpoofCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().groundSpoofCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger() || player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying() || player.onClimbable()) return;
        if (data.joinGraceTicks > 0) return;

        if (data.airTicks < MIN_AIR_TICKS) {
            data.groundSpoofBuffer = Math.max(0, data.groundSpoofBuffer - 1);
            return;
        }

        // Packet says onGround but server says AIR/FALLING for sustained ticks
        if (data.lastPacketHasPos && data.lastPacketOnGround && data.groundSpoofTicks >= SPOOF_TICKS_THRESHOLD) {
            // Additional verification: server's onGround() is false
            if (!player.onGround()) {
                data.groundSpoofBuffer++;
            } else {
                data.groundSpoofBuffer = Math.max(0, data.groundSpoofBuffer - 1);
            }
        } else {
            data.groundSpoofBuffer = Math.max(0, data.groundSpoofBuffer - 1);
        }

        if (data.groundSpoofBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("GroundSpoof: packet onGround=true but airTicks=%d spoofTicks=%d state=%s",
                            data.airTicks, data.groundSpoofTicks, data.movementState));
            data.groundSpoofBuffer = 0;
            data.groundSpoofTicks = 0;
        }
    }
}

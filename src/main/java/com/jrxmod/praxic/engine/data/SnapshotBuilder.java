package com.jrxmod.praxic.engine.data;

import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds an immutable PlayerSnapshot from the current ServerPlayer state
 * and the accumulated PlayerData for that player.
 *
 * Must be called after CheckManager.syncDerivedFields() so that
 * movementState / prevMovementState are already up-to-date.
 */
public final class SnapshotBuilder {

    private SnapshotBuilder() {}

    public static PlayerSnapshot build(ServerPlayer player, PlayerData data) {
        return new PlayerSnapshot(
                System.currentTimeMillis(),

                // Current position
                player.getX(), player.getY(), player.getZ(),

                // Previous position (stored by CheckManager at end of last tick)
                data.prevX, data.prevY, data.prevZ,

                // Current rotation
                player.getYRot(), player.getXRot(),

                // Previous rotation (stored by CheckManager at end of last tick)
                data.lastYaw, data.lastPitch,

                // Movement flags
                player.onGround(),
                player.isInWater(),
                player.onClimbable(),
                player.isFallFlying(),
                player.isPassenger(),

                // State machine — already updated by CheckManager this tick
                data.movementState,
                data.prevMovementState,

                // Combat / damage
                player.hurtTime,
                player.getHealth(),

                // Ping capped at 500 ms
                Math.min(player.connection.latency(), 500)
        );
    }
}

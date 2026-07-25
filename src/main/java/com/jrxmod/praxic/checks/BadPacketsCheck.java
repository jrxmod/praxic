package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Protocol sanity check for movement packets.
 *
 * Vanilla clients never send NaN / infinite coordinates and never send pitch
 * outside [-90, 90]. Most of those packets are rejected later by the server,
 * but recording them as PRAXIC evidence is useful because spoofed clients often
 * try to hide other modules behind invalid rotation or movement frames.
 */
public class BadPacketsCheck extends AbstractCheck {

    @Override
    public String getName() {
        return "BadPacketsCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via ServerGamePacketListenerMixin.onHandleMovePlayer().
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!Praxic.getConfig().badPacketsCheckEnabled) return;
        if (player.isDeadOrDying()) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;

        String reason = null;

        if (packet.hasRotation()) {
            float yaw = packet.getYRot(player.getYRot());
            float pitch = packet.getXRot(player.getXRot());

            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                reason = String.format("Non-finite rotation yaw=%s pitch=%s", yaw, pitch);
            } else if (pitch < -90.0f || pitch > 90.0f) {
                reason = String.format("Invalid pitch %.2f (allowed -90..90)", pitch);
            }
        }

        if (reason == null && packet.hasPosition()) {
            double x = packet.getX(player.getX());
            double y = packet.getY(player.getY());
            double z = packet.getZ(player.getZ());

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                reason = String.format("Non-finite position x=%s y=%s z=%s", x, y, z);
            } else if (Math.abs(x) > 3.2E7 || Math.abs(z) > 3.2E7) {
                reason = String.format("Position outside world border x=%.1f z=%.1f", x, z);
            }
        }

        if (reason == null) {
            data.badPacketBuffer = Math.max(0, data.badPacketBuffer - 1);
            return;
        }

        data.badPacketBuffer++;
        int threshold = Math.max(1, Praxic.getConfig().badPacketsBufferThreshold);
        if (data.badPacketBuffer >= threshold && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    reason + String.format(" (buffer: %d/%d)", data.badPacketBuffer, threshold));
            data.badPacketBuffer = 0;
        }
    }
}

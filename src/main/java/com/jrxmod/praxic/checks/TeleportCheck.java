package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects Blink / Teleport cheats by inspecting the position declared in move
 * packets before the vanilla server validates or corrects it.
 *
 * The distance is measured between the position claimed by the current packet
 * and the position claimed by the previous accepted packet. Smooth movement is
 * therefore never flagged, including a burst of packets arriving after a
 * network stall, because the client's own stream stays continuous. A jump
 * above the threshold with no recent server-initiated teleport is the
 * signature of a Blink / Teleport module.
 */
public class TeleportCheck extends AbstractCheck {

    /**
     * Maximum gap (ms) between movement packets before the baseline resets.
     * A larger gap means the client paused or the connection stalled, so a
     * resulting catch-up jump is legitimate and the baseline is reseeded.
     */
    private static final long MAX_PACKET_GAP_MS = 1000L;

    @Override
    public String getName() {
        return "TeleportCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via ServerGamePacketListenerMixin.onHandleMovePlayer().
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!Praxic.getConfig().teleportCheckEnabled) return;
        if (!packet.hasPosition()) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isFallFlying()) return;
        if (data.joinGraceTicks > 0) return;

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        long now = System.currentTimeMillis();

        // A server-initiated teleport (ender pearl, chorus fruit, /tp, portal,
        // respawn) is confirmed by the client, which grants a grace window that
        // exempts the resulting position jump. The baseline is reseeded here so
        // the next packet measures from the new position.
        if (data.teleportGraceTicks > 0) {
            data.teleportGraceTicks--;
            data.lastPacketX = x;
            data.lastPacketY = y;
            data.lastPacketZ = z;
            data.lastPacketTime = now;
            return;
        }

        if (data.lastPacketTime == 0L) {
            // First position packet — seed the baseline, nothing to compare.
            data.lastPacketX = x;
            data.lastPacketY = y;
            data.lastPacketZ = z;
            data.lastPacketTime = now;
            return;
        }

        long gap = now - data.lastPacketTime;
        double dx = x - data.lastPacketX;
        double dy = y - data.lastPacketY;
        double dz = z - data.lastPacketZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (gap > MAX_PACKET_GAP_MS) {
            // Client paused or connection stalled — a large jump here is a
            // legitimate catch-up, not a cheat. Reseed without flagging.
            data.lastPacketX = x;
            data.lastPacketY = y;
            data.lastPacketZ = z;
            data.lastPacketTime = now;
            return;
        }

        double maxDistance = Praxic.getConfig().teleportMaxBlocksPerTick;
        if (distance > maxDistance && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Moved %.1f blocks in one packet (max: %.1f)",
                            distance, maxDistance));
        }

        data.lastPacketX = x;
        data.lastPacketY = y;
        data.lastPacketZ = z;
        data.lastPacketTime = now;
    }
}

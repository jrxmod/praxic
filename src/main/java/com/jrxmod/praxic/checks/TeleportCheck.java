package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Detects Blink / Teleport cheats.
 *
 * Blink does not send one giant packet. It holds every move packet and
 * dumps them in a burst when disabled. Consecutive packets stay small, but
 * vanilla still kicks {@code moved too quickly} because the sum in one tick
 * exceeds ~10 blocks. Comparing each packet to the tick-start origin catches
 * that burst. Elytra / passengers reseed without flagging.
 */
public class TeleportCheck extends AbstractCheck {

    @Override
    public String getName() {
        return "TeleportCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via ServerGamePacketListenerMixin.onHandleMovePlayer().
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!packet.hasPosition()) return;

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        long now = System.currentTimeMillis();

        if (!Praxic.getConfig().teleportCheckEnabled
                || player.isSpectator()
                || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                || player.isDeadOrDying()
                || player.isPassenger()
                || player.isFallFlying()
                || player.isAutoSpinAttack()
                || data.joinGraceTicks > 0
                || data.teleportGraceTicks > 0
                || (Praxic.getImpulseEngine() != null
                    && Praxic.getImpulseEngine().isActive(player.getUUID()))) {
            if (data.teleportGraceTicks > 0) data.teleportGraceTicks--;
            reseed(data, x, y, z, now);
            return;
        }

        if (data.lastPacketTime == 0L) {
            reseed(data, x, y, z, now);
            return;
        }

        double max = Praxic.getConfig().teleportMaxBlocksPerTick;
        double fromLast = dist(x, y, z, data.lastPacketX, data.lastPacketY, data.lastPacketZ);
        double fromPlayer = dist(x, y, z, player.getX(), player.getY(), player.getZ());
        double fromTick = data.tickOriginSet
                ? dist(x, y, z, data.tickOriginX, data.tickOriginY, data.tickOriginZ)
                : 0.0;

        double worst = Math.max(fromLast, Math.max(fromPlayer, fromTick));
        double dy = y - data.lastPacketY;
        // Vertical-only climbs are Step, not Blink.
        boolean verticalStep = fromLast > 0.01 && Math.abs(dy) >= fromLast * 0.85 && fromLast < 12.0;
        if (worst > max && !verticalStep && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Moved %.1f blocks (packet %.1f, tick %.1f, max %.1f)",
                            worst, fromLast, fromTick, max));
        }

        reseed(data, x, y, z, now);
    }

    private static double dist(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void reseed(PlayerData data, double x, double y, double z, long now) {
        data.lastPacketX = x;
        data.lastPacketY = y;
        data.lastPacketZ = z;
        data.lastPacketTime = now;
    }
}

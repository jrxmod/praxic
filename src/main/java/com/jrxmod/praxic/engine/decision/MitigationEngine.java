package com.jrxmod.praxic.engine.decision;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Set;

/**
 * Packet-level mitigation: reject illegal movement before vanilla applies it
 * and rubberband the client to the last safe position.
 *
 * Pure predicates are unit-tested without Minecraft. Player-facing methods
 * skip Impulse windows, join grace, creative and whitelist.
 */
public class MitigationEngine {

    private static final int MOVE_BUFFER = 2;
    private static final int SETBACK_GRACE_TICKS = 20;

    /**
     * Hover / fly packet: airborne longer than the fly threshold and not
     * falling. Downward packets are always allowed so natural falls pass.
     */
    public static boolean rejectHover(int airTicks, int maxAirTicks, double dy) {
        return airTicks > maxAirTicks && dy >= -0.05;
    }

    /**
     * Horizontal packet jump above the speed cap but below the teleport cap.
     * Teleport-sized jumps are handled by rejectTeleport.
     */
    public static boolean rejectSpeed(double horizontal, double maxSpeed, double teleportCap) {
        return horizontal > maxSpeed && horizontal <= teleportCap;
    }

    /**
     * Single-packet blink. Teleport grace (pearls, /tp) is never rejected.
     * A long gap is not an exemption: Blink holds packets then delivers one
     * large jump. Elytra / Impulse must skip via {@link #canMitigate}.
     */
    public static boolean rejectTeleport(double distance, double maxDistance,
                                         boolean teleportGrace, long gapMs) {
        if (teleportGrace) return false;
        return distance > maxDistance;
    }

    /**
     * Evaluates a movement packet. When rejected, vanilla must not apply it
     * ({@code ci.cancel()} in the mixin) and the client is rubberbanded.
     *
     * @return true if the packet must be cancelled
     */
    public boolean tryRejectMove(ServerPlayer player, PlayerData data,
                                 ServerboundMovePlayerPacket packet,
                                 AbstractCheck flyCheck, AbstractCheck speedCheck,
                                 AbstractCheck teleportCheck) {
        PraxicConfig cfg = Praxic.getConfig();
        if (cfg == null || !cfg.enableMitigation) return false;
        if (!packet.hasPosition()) return false;
        if (!canMitigate(player, data)) return false;

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        long now = System.currentTimeMillis();

        if (data.lastPacketTime == 0L) {
            return false;
        }
        if (data.teleportGraceTicks > 0) {
            return false;
        }

        long gap = now - data.lastPacketTime;
        double dx = x - data.lastPacketX;
        double dy = y - data.lastPacketY;
        double dz = z - data.lastPacketZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean grace = data.teleportGraceTicks > 0;

        int ping = player.connection.latency();
        double maxSpeed = (cfg.speedMaxBlocksPerTick + LagCompensation.extraSpeed(ping))
                * LagCompensation.tpsSensitivity();
        int maxAir = cfg.flyMaxAirTicks + LagCompensation.extraAirTicks(ping);

        String reason = null;
        AbstractCheck check = null;

        if (cfg.teleportCheckEnabled
                && rejectTeleport(distance, cfg.teleportMaxBlocksPerTick, grace, gap)) {
            reason = String.format("mitigate teleport %.1f blocks", distance);
            check = teleportCheck;
        } else if (cfg.speedCheckEnabled
                && gap <= 100L
                && rejectSpeed(horizontal, maxSpeed, cfg.teleportMaxBlocksPerTick)) {
            reason = String.format("mitigate speed %.3f b/packet (max %.3f)", horizontal, maxSpeed);
            check = speedCheck;
        } else if (cfg.flyCheckEnabled
                && rejectHover(data.airTicks, maxAir, dy)) {
            reason = String.format("mitigate hover airTicks=%d dy=%.3f", data.airTicks, dy);
            check = flyCheck;
        }

        if (reason == null) {
            data.mitigateMoveBuffer = Math.max(0, data.mitigateMoveBuffer - 1);
            return false;
        }

        data.mitigateMoveBuffer++;
        if (data.mitigateMoveBuffer < MOVE_BUFFER) return false;

        data.mitigateMoveBuffer = 0;
        if (check != null && data.canFlag(check.getName() + "_mitigate", 2000)) {
            ViolationManager.flag(player, data, check, reason);
        }
        rubberband(player, data);
        return true;
    }

    public void rubberband(ServerPlayer player, PlayerData data) {
        data.teleportGraceTicks = SETBACK_GRACE_TICKS;
        player.connection.teleport(
                data.lastSafeX, data.lastSafeY, data.lastSafeZ,
                player.getYRot(), player.getXRot(), Set.of());
    }

    public boolean canMitigate(ServerPlayer player, PlayerData data) {
        if (player.isSpectator()) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (player.getAbilities().mayfly) return false;
        if (player.isDeadOrDying()) return false;
        if (player.isPassenger()) return false;
        if (player.isFallFlying()) return false;
        if (player.isAutoSpinAttack()) return false;
        if (data.joinGraceTicks > 0) return false;
        if (data.freezeTicksRemaining > 0) return false;
        if (Praxic.getWhitelistManager() != null
                && Praxic.getWhitelistManager().isWhitelisted(player.getUUID())) return false;
        if (Praxic.getImpulseEngine() != null
                && Praxic.getImpulseEngine().isActive(player.getUUID())) return false;
        return true;
    }
}

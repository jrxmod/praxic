package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hover / fly. Does not trust {@code onGround} or the movement-state airTicks
 * counter: Flight cheats keep the player airborne while vanilla still kicks
 * only after its own float timer (and Anti-Kick can stretch that to tens of
 * seconds). Support is a real collision or liquid within 0.3 blocks below.
 */
public class FlyCheck extends AbstractCheck {

    /** Packet-path hover threshold: 15 packets at 20 TPS is ~0.75 s. */
    private static final int PACKET_HOVER_TICKS = 15;

    /**
     * Sustained upward movement without support. A wall-climb module keeps
     * the client velocity at {@code y=0.2} every tick while against a wall,
     * which looks like a very long, flat ascent; a vanilla jump decays after
     * a few ticks and never reaches this streak.
     */
    private static final int CLIMB_STREAK_TICKS = 25;
    private static final double CLIMB_MIN_DY = 0.1;

    @Override
    public String getName() {
        return "FlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().flyCheckEnabled) return;

        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                || player.getAbilities().mayfly || player.isPassenger() || player.isFallFlying()
                || player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.isAutoSpinAttack() || data.joinGraceTicks > 0
                // Dismounting can leave the player on a boat or block edge
                // for a moment; the 1s settling window is not hover.
                || data.recentVehicleExit()
                // While a ground-spoof is being detected the onGround bits
                // are unreliable for support checks; the no-fall detector
                // owns that signal.
                || data.noFallSpoofTicks > 0) {
            data.unsupportedAirTicks = 0;
            return;
        }
        // Do not skip Impulse: a stuck window would freeze unsupportedAirTicks at 0.

        if (hasSupport(player)) {
            data.unsupportedAirTicks = 0;
            data.climbTicks = 0;
            return;
        }

        double dy = player.getY() - data.prevY;

        // Sustained ascent along a wall  -  wall-climb signature. The jump
        // impulse is larger but short; this tracker only fires on sustained
        // upward motion that never returns to falling.
        if (dy > CLIMB_MIN_DY) {
            data.climbTicks++;
        } else {
            data.climbTicks = Math.max(0, data.climbTicks - 1);
        }
        if (data.climbTicks >= CLIMB_STREAK_TICKS && data.canFlag(getName() + "_climb", 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Sustained vertical climb without support for %d ticks (dy=%.3f)",
                            data.climbTicks, dy));
            data.climbTicks = 0;
        }

        data.unsupportedAirTicks++;
        int ping = player.connection.latency();
        int maxAir = Praxic.getConfig().flyMaxAirTicks + LagCompensation.extraAirTicks(ping);

        // Natural freefall accelerates below -0.1 within a few ticks.
        if (data.unsupportedAirTicks > maxAir && dy >= -0.10 && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Hover without support for %d ticks (dy=%.3f, ping=%dms)",
                            data.unsupportedAirTicks, dy, ping));
        }
    }

    /**
     * Client sent {@code flying=true} without the mayfly ability.
     */
    public boolean onIllegalFlyingFlag(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().flyCheckEnabled) return false;
        if (player.isSpectator()) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (player.getAbilities().mayfly) return false;
        if (data.joinGraceTicks > 0) return false;
        if (data.canFlag(getName() + "_abilities", 2000)) {
            ViolationManager.flag(player, data, this, "Client set flying=true without mayfly");
        }
        return Praxic.getConfig().enableMitigation;
    }

    private static boolean hasSupport(ServerPlayer player) {
        // The server validated the collision when it set onGround; the coarse
        // block scan below misses partial platforms (slabs, carpets, trapdoors,
        // edge of a block), which flagged players standing on a ledge above a
        // cliff as "hover".
        if (player.onGround()) return true;
        if (player.isInWater() || player.isInLava() || player.onClimbable()) return true;
        double y = player.getY();
        BlockPos feet = BlockPos.containing(player.getX(), y - 0.15, player.getZ());
        BlockPos below = feet.below();
        return isSolid(player, feet) || isSolid(player, below);
    }

    private static boolean isSolid(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        return !state.getCollisionShape(player.level(), pos).isEmpty();
    }

    /**
     * Packet-level hover. Same path as TeleportCheck, before vanilla rubberband.
     */
    public void onMovePacket(ServerPlayer player,
                             net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet,
                             PlayerData data) {
        if (!Praxic.getConfig().flyCheckEnabled) return;
        if (!packet.hasPosition()) return;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                || player.getAbilities().mayfly || player.isPassenger() || player.isFallFlying()
                || player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)
                || data.joinGraceTicks > 0 || data.recentVehicleExit()
                || data.noFallSpoofTicks > 0) {
            data.packetHoverTicks = 0;
            return;
        }

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        double dy = data.lastPacketTime == 0L ? 0.0 : y - data.lastPacketY;

        // onGround=true means the client (and server, after processing this
        // packet) sees collision below; a hover/NoFall packet-spoof is a
        // different signal and must not be counted as unsupported flight.
        if (packet.isOnGround() || hasSupportAt(player, x, y, z) || dy < -0.12) {
            data.packetHoverTicks = 0;
            return;
        }
        data.packetHoverTicks++;
        if (data.packetHoverTicks >= PACKET_HOVER_TICKS && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Packet hover %d without support dy=%.3f y=%.2f",
                            data.packetHoverTicks, dy, y));
        }
    }

    private static boolean hasSupportAt(ServerPlayer player, double x, double y, double z) {
        if (player.isInWater() || player.isInLava() || player.onClimbable()) return true;
        BlockPos feet = BlockPos.containing(x, y - 0.2, z);
        return isSolid(player, feet) || isSolid(player, feet.below());
    }
}

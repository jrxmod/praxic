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

    private static final int HOVER_TICKS = 15;

    @Override
    public String getName() {
        return "FlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().flyCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (player.isPassenger()) return;
        if (player.isFallFlying()) return;
        if (player.hasEffect(MobEffects.LEVITATION)) return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;
        if (player.isAutoSpinAttack()) return;
        if (data.joinGraceTicks > 0) return;
        // Do not skip Impulse: a stuck window would freeze unsupportedAirTicks at 0.

        if (hasSupport(player)) {
            data.unsupportedAirTicks = 0;
            return;
        }

        data.unsupportedAirTicks++;
        double dy = player.getY() - data.prevY;
        int ping = player.connection.latency();
        int maxAir = HOVER_TICKS + LagCompensation.extraAirTicks(ping);

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
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly || player.isPassenger() || player.isFallFlying()) return;
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) return;
        if (data.joinGraceTicks > 0) return;

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        double dy = data.lastPacketTime == 0L ? 0.0 : y - data.lastPacketY;

        if (hasSupportAt(player, x, y, z) || dy < -0.12) {
            data.unsupportedAirTicks = 0;
            return;
        }
        data.unsupportedAirTicks++;
        if (data.unsupportedAirTicks >= HOVER_TICKS && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Packet hover %d without support dy=%.3f y=%.2f",
                            data.unsupportedAirTicks, dy, y));
        }
    }

    private static boolean hasSupportAt(ServerPlayer player, double x, double y, double z) {
        if (player.isInWater() || player.isInLava() || player.onClimbable()) return true;
        BlockPos feet = BlockPos.containing(x, y - 0.2, z);
        return isSolid(player, feet) || isSolid(player, feet.below());
    }
}

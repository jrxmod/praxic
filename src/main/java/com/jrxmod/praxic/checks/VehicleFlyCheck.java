package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;

/**
 * Detects flying while riding a non-boat vehicle (horse, pig, strider, minecart).
 * BoatFlyCheck remains responsible for boats so the two signatures stay separate.
 *
 * Minecarts on rails are exempt. Natural freefall resets the hover counter.
 */
public class VehicleFlyCheck extends AbstractCheck {

    private static final int HOVER_TICKS_THRESHOLD = 25;
    private static final double FREEFALL_DY_THRESHOLD = -0.3;

    @Override
    public String getName() {
        return "VehicleFlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().vehicleFlyCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (data.joinGraceTicks > 0) {
            data.vehicleFlyTicks = 0;
            return;
        }
        if (Praxic.getImpulseEngine().isActive(player.getUUID())) {
            data.vehicleFlyTicks = 0;
            return;
        }

        if (!player.isPassenger()) {
            data.vehicleFlyTicks = 0;
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle == null || vehicle instanceof Boat) {
            data.vehicleFlyTicks = 0;
            return;
        }
        if (!isTrackedVehicle(vehicle)) {
            data.vehicleFlyTicks = 0;
            return;
        }

        if (vehicle instanceof AbstractMinecart && isOnRail(vehicle)) {
            data.vehicleFlyTicks = 0;
            return;
        }

        if (vehicle.onGround() || vehicle.isInWater() || vehicle.isInLava()) {
            data.vehicleFlyTicks = 0;
            return;
        }

        double dy = player.getY() - data.prevY;
        if (dy < FREEFALL_DY_THRESHOLD) {
            data.vehicleFlyTicks = 0;
            return;
        }

        data.vehicleFlyTicks++;
        if (data.vehicleFlyTicks >= HOVER_TICKS_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Vehicle %s hovering for %d ticks at Y=%.2f (dy=%.3f)",
                            vehicle.getType().toShortString(), data.vehicleFlyTicks, vehicle.getY(), dy));
        }
    }

    private static boolean isTrackedVehicle(Entity vehicle) {
        return vehicle instanceof AbstractHorse
                || vehicle instanceof Pig
                || vehicle instanceof Strider
                || vehicle instanceof AbstractMinecart;
    }

    private static boolean isOnRail(Entity vehicle) {
        var level = vehicle.level();
        var pos = vehicle.blockPosition();
        return level.getBlockState(pos).is(BlockTags.RAILS)
                || level.getBlockState(pos.below()).is(BlockTags.RAILS);
    }

    /**
     * Same packet path as BoatFlyCheck. Horse/pig fly never shows up on
     * player move packets.
     */
    public void onVehiclePacket(ServerPlayer player,
                                net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet,
                                PlayerData data) {
        if (!Praxic.getConfig().vehicleFlyCheckEnabled) return;
        if (!player.isPassenger()) {
            data.vehicleFlyTicks = 0;
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || vehicle instanceof Boat || !isTrackedVehicle(vehicle)) {
            data.vehicleFlyTicks = 0;
            return;
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;
        if (vehicle instanceof AbstractMinecart && isOnRail(vehicle)) {
            data.vehicleFlyTicks = 0;
            return;
        }

        double y = packet.getY();
        double dy = data.lastVehicleTime == 0L ? 0.0 : y - data.lastVehicleY;
        data.lastVehicleY = y;
        data.lastVehicleTime = System.currentTimeMillis();

        if (vehicle.onGround() || vehicle.isInWater() || vehicle.isInLava()) {
            data.vehicleFlyTicks = 0;
            return;
        }
        if (dy < FREEFALL_DY_THRESHOLD) {
            data.vehicleFlyTicks = 0;
            return;
        }
        data.vehicleFlyTicks++;
        if (data.vehicleFlyTicks >= HOVER_TICKS_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Vehicle packet hover %s %d at Y=%.2f dy=%.3f",
                            vehicle.getType().toShortString(), data.vehicleFlyTicks, y, dy));
        }
    }
}

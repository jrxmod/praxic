package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * BoatFly sets vehicle motionY to 0 (hover) or +0.3 (jump). A falling
 * boat has negative dy; a boat on water has fluid at the hull, not one block
 * below.
 */
public class BoatFlyCheck extends AbstractCheck {

    private static final int HOVER_TICKS_THRESHOLD = 12;
    private static final double FREEFALL_DY_THRESHOLD = -0.08;

    @Override
    public String getName() {
        return "BoatFlyCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().boatFlyCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;

        if (data.joinGraceTicks > 0) {
            data.boatAirTicks = 0;
            return;
        }

        if (!player.isPassenger()) {
            data.boatAirTicks = 0;
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat)) {
            data.boatAirTicks = 0;
            return;
        }

        if (hullHasSupport(vehicle) || vehicle.onGround() || vehicle.isInWater()) {
            data.boatAirTicks = 0;
            return;
        }

        double dy = player.getY() - data.prevY;
        if (dy < FREEFALL_DY_THRESHOLD) {
            data.boatAirTicks = 0;
            return;
        }

        data.boatAirTicks++;
        if (data.boatAirTicks >= HOVER_TICKS_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Boat hovering for %d ticks at Y=%.2f (dy=%.3f)",
                            data.boatAirTicks, vehicle.getY(), dy));
        }
    }

    private static boolean hullHasSupport(Entity vehicle) {
        AABB box = vehicle.getBoundingBox();
        double x = vehicle.getX();
        double z = vehicle.getZ();
        BlockPos hull = BlockPos.containing(x, box.minY + 0.05, z);
        if (!vehicle.level().getFluidState(hull).isEmpty()) return true;
        BlockPos atY = BlockPos.containing(x, vehicle.getY() - 0.15, z);
        if (!vehicle.level().getFluidState(atY).isEmpty()) return true;
        BlockPos below = hull.below();
        return !vehicle.level().getBlockState(below).getCollisionShape(vehicle.level(), below).isEmpty()
                && box.minY - below.getY() < 1.05;
    }

    /**
     * Vehicle-move packet. BoatFly writes client vehicle motion and
     * sends ServerboundMoveVehiclePacket; player move packets never see it.
     *
     * @return true if the packet should be cancelled
     */
    public boolean onVehiclePacket(ServerPlayer player,
                                   net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet,
                                   PlayerData data) {
        if (!Praxic.getConfig().boatFlyCheckEnabled) return false;
        if (!player.isPassenger()) {
            data.boatAirTicks = 0;
            return false;
        }
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat)) {
            data.boatAirTicks = 0;
            return false;
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (data.joinGraceTicks > 0) return false;

        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();

        long now = System.currentTimeMillis();
        if (data.lastVehicleTime != 0L
                && now - data.lastVehicleTime < 40L
                && Math.abs(x - data.lastVehicleX) < 1.0E-4
                && Math.abs(y - data.lastVehicleY) < 1.0E-4
                && Math.abs(z - data.lastVehicleZ) < 1.0E-4) {
            return false;
        }

        double dy = data.lastVehicleTime == 0L ? 0.0 : y - data.lastVehicleY;
        data.lastVehicleX = x;
        data.lastVehicleY = y;
        data.lastVehicleZ = z;
        data.lastVehicleTime = now;

        BlockPos hull = BlockPos.containing(x, y - 0.15, z);
        boolean inFluid = !player.level().getFluidState(hull).isEmpty()
                || !player.level().getFluidState(BlockPos.containing(x, y, z)).isEmpty()
                || vehicle.isInWater();
        BlockPos below = hull.below();
        boolean solid = !player.level().getBlockState(below)
                .getCollisionShape(player.level(), below).isEmpty()
                && y - below.getY() < 1.05;

        if (inFluid || solid || vehicle.onGround()) {
            data.boatAirTicks = 0;
            return false;
        }
        if (dy < FREEFALL_DY_THRESHOLD) {
            data.boatAirTicks = 0;
            return false;
        }
        data.boatAirTicks++;
        if (data.boatAirTicks >= HOVER_TICKS_THRESHOLD && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Vehicle packet hover %d at Y=%.2f dy=%.3f",
                            data.boatAirTicks, y, dy));
            return Praxic.getConfig().enableMitigation;
        }
        return false;
    }
}

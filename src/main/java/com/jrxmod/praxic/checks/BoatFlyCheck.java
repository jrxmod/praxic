package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;

public class BoatFlyCheck extends AbstractCheck {

    // Ticks the boat must be in hover state before flagging.
    private static final int HOVER_TICKS_THRESHOLD = 20;

    // If the boat is falling faster than this per tick, it is in natural freefall — not flying.
    // Real BoatFly keeps dy near 0 or positive. Legitimate falls quickly exceed this.
    private static final double FREEFALL_DY_THRESHOLD = -0.3;

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

        // Skip until join grace expires — prevY is unreliable on first ticks
        if (data.joinGraceTicks > 0) {
            data.boatAirTicks = 0;
            return;
        }

        // Player must be riding a boat (Boat covers both Boat and ChestBoat)
        if (!player.isPassenger()) {
            data.boatAirTicks = 0;
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat)) {
            data.boatAirTicks = 0;
            return;
        }

        // Boat is legitimate if it is on the ground or in water
        if (vehicle.onGround() || vehicle.isInWater()) {
            data.boatAirTicks = 0;
            return;
        }

        // Compute vertical displacement this tick using player position (player rides the boat)
        double dy = player.getY() - data.prevY;

        // Natural freefall: dy becomes increasingly negative — reset counter, not a hack
        if (dy < FREEFALL_DY_THRESHOLD) {
            data.boatAirTicks = 0;
            return;
        }

        // Boat is hovering / ascending this tick — suspicious
        data.boatAirTicks++;

        if (data.boatAirTicks >= HOVER_TICKS_THRESHOLD
                && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Boat hovering for %d ticks at Y=%.2f (dy=%.3f)",
                            data.boatAirTicks, vehicle.getY(), dy));
        }
    }
}

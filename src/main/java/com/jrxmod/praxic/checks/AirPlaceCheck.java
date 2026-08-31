package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Detects AirPlace: successful block placement against air or beyond vanilla
 * block-interaction range. Vanilla requires a real block face within
 * {@code blockInteractionRange()} (4.5 in survival).
 *
 * Mitigation cancels air and out-of-range hits at HEAD so vanilla never
 * applies them. Clicking air with a block in hand is a normal miss and is
 * cancelled silently; only successful (or out-of-range block) placements flag.
 */
public class AirPlaceCheck extends AbstractCheck {

    private static final int BUFFER_THRESHOLD = 2;

    @Override
    public String getName() {
        return "AirPlaceCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        // Event-driven via ServerPlayerGameModeMixin.
    }

    /**
     * @return true if the interaction should be cancelled before vanilla
     */
    public boolean shouldCancel(ServerPlayer player, BlockHitResult hit, PlayerData data) {
        if (!Praxic.getConfig().enableMitigation) return false;
        if (!Praxic.getConfig().airPlaceCheckEnabled) return false;
        if (player.isSpectator()) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (player.isDeadOrDying()) return false;
        if (data.joinGraceTicks > 0) return false;
        if (Praxic.getWhitelistManager() != null
                && Praxic.getWhitelistManager().isWhitelisted(player.getUUID())) return false;

        Vec3 eye = player.getEyePosition();
        double distance = eye.distanceTo(hit.getLocation());
        double max = player.blockInteractionRange()
                + 0.75
                + LagCompensation.extraReach(player.connection.latency());

        boolean clickedAir = player.level().getBlockState(hit.getBlockPos()).isAir();
        boolean tooFar = distance > max;
        return clickedAir || tooFar;
    }

    public void onBlockPlace(ServerPlayer player, BlockHitResult hit, PlayerData data) {
        if (!Praxic.getConfig().airPlaceCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        Vec3 eye = player.getEyePosition();
        Vec3 loc = hit.getLocation();
        double distance = eye.distanceTo(loc);
        double max = player.blockInteractionRange()
                + 0.75
                + LagCompensation.extraReach(player.connection.latency());

        boolean clickedAir = player.level().getBlockState(hit.getBlockPos()).isAir();
        boolean tooFar = distance > max;

        if (!clickedAir && !tooFar) {
            data.airPlaceBuffer = Math.max(0, data.airPlaceBuffer - 1);
            return;
        }

        data.airPlaceBuffer++;
        if (data.airPlaceBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            String reason = clickedAir
                    ? String.format("Placed against air at %s (%.2f blocks)", hit.getBlockPos().toShortString(), distance)
                    : String.format("Place distance %.2f (max %.2f)", distance, max);
            ViolationManager.flag(player, data, this, reason);
            data.airPlaceBuffer = 0;
        }
    }
}

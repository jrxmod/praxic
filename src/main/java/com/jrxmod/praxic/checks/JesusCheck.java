package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

public class JesusCheck extends AbstractCheck {

    // Minimum horizontal movement to consider player is actively walking on water
    private static final double MIN_HORIZONTAL_SPEED = 0.05;

    @Override
    public String getName() {
        return "JesusCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().jesusCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.getAbilities().mayfly) return;
        if (player.isFallFlying()) return;

        // Skip recent knockback — can push player over water edge
        if (player.hurtTime > 0) return;

        boolean inWater = player.isInWater();

        // Grace handled centrally in CheckManager.syncDerivedFields()
        if (inWater) return;
        if (data.wasInWater) return;
        if (data.jesusWaterGraceTicks > 0) return;

        // Player is falling down — not walking on water
        double dy = player.getY() - data.prevY;
        if (dy < -0.01) return;

        BlockPos footPos = player.blockPosition();

        // Check if foot-level block is water (player walking on surface)
        boolean footInWater = player.level().getBlockState(footPos)
                .getFluidState().is(Fluids.WATER)
                || player.level().getBlockState(footPos)
                .getFluidState().is(Fluids.FLOWING_WATER);

        if (!footInWater) return;

        // Frost Walker enchantment creates frosted ice below
        if (player.level().getBlockState(footPos.below()).is(Blocks.FROSTED_ICE)) return;

        // Lily pad: player stands on lily pad block placed on water surface.
        // Actual lily pad is at footPos, water check would be at footPos below.
        if (player.level().getBlockState(footPos).is(Blocks.LILY_PAD)) return;
        if (player.level().getBlockState(footPos.below()).is(Blocks.LILY_PAD)) return;

        // Must be moving horizontally
        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        if (horizontalSpeed < MIN_HORIZONTAL_SPEED) return;

        if (data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Walking on water surface, speed=%.3f", horizontalSpeed));
        }
    }
}

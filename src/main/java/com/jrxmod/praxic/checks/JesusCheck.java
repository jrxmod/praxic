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

    // Grace ticks after leaving water — independent from FlyCheck waterExitTicks
    private static final int WATER_GRACE_TICKS = 15;

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

        // Update independent jesus grace timer
        if (data.wasInWater && !inWater) {
            data.jesusWaterGraceTicks = WATER_GRACE_TICKS;
        }
        if (data.jesusWaterGraceTicks > 0) {
            data.jesusWaterGraceTicks--;
        }

        // Player is already legitimately in water — not Jesus
        if (inWater) return;

        // Was in water last tick — transition frame, skip
        if (data.wasInWater) return;

        // Still in grace period after leaving water
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

        // Frost Walker enchantment creates frosted ice below — not Jesus
        if (player.level().getBlockState(footPos.below()).is(Blocks.FROSTED_ICE)) return;

        // Lily pad — player stands on block placed on water, not Jesus
        if (player.level().getBlockState(footPos).is(Blocks.LILY_PAD)) return;

        // Must be moving horizontally — stationary players can briefly clip water edges
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

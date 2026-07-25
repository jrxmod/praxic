package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import com.jrxmod.praxic.util.LagCompensation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Detects NoSlow modules: moving at near-normal speed while using items that
 * vanilla slows down (food, bow, shield, crossbow, trident, etc.).
 */
public class NoSlowCheck extends AbstractCheck {

    private static final int BUFFER_THRESHOLD = 5;
    private static final int BUFFER_DECAY = 1;

    @Override
    public String getName() {
        return "NoSlowCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().noSlowCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.getAbilities().mayfly) return;
        if (player.isPassenger()) return;
        if (player.isFallFlying()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.hurtTime > 0) return;
        if (data.joinGraceTicks > 0) return;

        if (!player.isUsingItem()) {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - BUFFER_DECAY);
            return;
        }

        // Ice naturally preserves momentum and can exceed no-slow limits.
        BlockPos below = player.blockPosition().below();
        var blockBelow = player.level().getBlockState(below).getBlock();
        if (blockBelow == Blocks.ICE
                || blockBelow == Blocks.PACKED_ICE
                || blockBelow == Blocks.BLUE_ICE
                || blockBelow == Blocks.FROSTED_ICE) {
            data.noSlowBuffer = 0;
            return;
        }

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        int ping = player.connection.latency();
        double maxSpeed = Praxic.getConfig().noSlowMaxBlocksPerTick
                + LagCompensation.extraSpeed(ping) * 0.35;

        if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            int amplifier = player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
            maxSpeed *= (1.0 + 0.2 * (amplifier + 1));
        }

        if (horizontal > maxSpeed) {
            data.noSlowBuffer++;
        } else {
            data.noSlowBuffer = Math.max(0, data.noSlowBuffer - BUFFER_DECAY);
        }

        if (data.noSlowBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Using item while moving %.3f b/t (max: %.3f, buffer: %d, ping: %dms)",
                            horizontal, maxSpeed, data.noSlowBuffer, ping));
            data.noSlowBuffer = 0;
        }
    }
}

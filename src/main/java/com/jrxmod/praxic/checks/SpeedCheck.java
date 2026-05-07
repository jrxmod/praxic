package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class SpeedCheck extends AbstractCheck {

    // Skip check if tick took longer than this (server lag protection)
    private static final long MAX_TICK_DELTA_MS = 100;

    // Skip if distance suggests teleport or severe lag
    private static final double TELEPORT_THRESHOLD = 6.0;

    // Flag only after this many consecutive suspicious ticks
    private static final int REQUIRED_BUFFER = 2;

    // Decrease buffer by this amount on normal movement
    private static final int BUFFER_DECAY = 1;

    @Override
    public String getName() {
        return "SpeedCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().speedCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;

        // Skip if player was recently hit — knockback causes false positives
        if (player.hurtTime > 0) return;

        // Skip on server lag
        long now = System.currentTimeMillis();
        long delta = now - data.lastPositionUpdate;
        if (delta > MAX_TICK_DELTA_MS) return;

        // Skip on ice — high slipperiness causes natural speed buildup beyond threshold
        BlockPos below = player.blockPosition().below();
        var blockBelow = player.level().getBlockState(below).getBlock();
        if (blockBelow == Blocks.ICE
                || blockBelow == Blocks.PACKED_ICE
                || blockBelow == Blocks.BLUE_ICE
                || blockBelow == Blocks.FROSTED_ICE) return;

        double dx = player.getX() - data.prevX;
        double dz = player.getZ() - data.prevZ;
        double distancePerTick = Math.sqrt(dx * dx + dz * dz);

        // Skip teleports and severe lag jumps
        if (distancePerTick > TELEPORT_THRESHOLD) return;

        // Base threshold from config
        double maxSpeed = Praxic.getConfig().speedMaxBlocksPerTick;

        // Scale threshold with speed effect
        if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            int amplifier = player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier();
            maxSpeed *= (1.0 + 0.2 * (amplifier + 1));
        }

        if (distancePerTick > maxSpeed) {
            data.speedBuffer++;

            boolean canFlag = data.speedBuffer >= REQUIRED_BUFFER;
            if (canFlag && data.canFlag(getName(), 2000)) {
                ViolationManager.flag(player, data, this,
                        String.format("Speed: %.3f blocks/tick (max: %.3f)", distancePerTick, maxSpeed));
                data.speedBuffer = 0;
            }
        } else {
            data.speedBuffer = Math.max(0, data.speedBuffer - BUFFER_DECAY);
        }
    }
}

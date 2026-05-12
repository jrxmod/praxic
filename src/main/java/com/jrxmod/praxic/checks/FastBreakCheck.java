package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

public class FastBreakCheck extends AbstractCheck {

    // Generous margin — flag only if break was faster than 40% of minimum expected time
    // Configured via fastBreakSpeedMultiplier (default 0.4)

    @Override
    public String getName() {
        return "FastBreakCheck";
    }

    // Tick-based check not used — fully event-driven via onStartBreak / onStopBreak
    @Override
    public void check(ServerPlayer player, PlayerData data) {}

    public void onStartBreak(ServerPlayer player, BlockPos pos, PlayerData data) {
        if (!Praxic.getConfig().fastBreakCheckEnabled) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        data.breakStartTime = System.currentTimeMillis();
        data.breakingBlockPos = pos;
    }

    public void onStopBreak(ServerPlayer player, BlockPos pos, PlayerData data) {
        if (!Praxic.getConfig().fastBreakCheckEnabled) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.breakStartTime == 0 || data.breakingBlockPos == null) return;

        // Only evaluate if stop matches the block we started breaking
        if (!pos.equals(data.breakingBlockPos)) {
            data.breakStartTime = 0;
            data.breakingBlockPos = null;
            return;
        }

        long elapsed = System.currentTimeMillis() - data.breakStartTime;
        data.breakStartTime = 0;
        data.breakingBlockPos = null;

        BlockState state = player.serverLevel().getBlockState(pos);
        float hardness = state.getDestroySpeed(player.serverLevel(), pos);

        // Skip insta-mine blocks (hardness <= 0) — designed to break instantly
        if (hardness <= 0) return;

        // Calculate tool speed multiplier
        ItemStack tool = player.getMainHandItem();
        float toolSpeed = tool.getDestroySpeed(state);
        if (toolSpeed < 1.0f) toolSpeed = 1.0f;

        // Apply Haste effect
        if (player.hasEffect(MobEffects.DIG_SPEED)) {
            int amplifier = player.getEffect(MobEffects.DIG_SPEED).getAmplifier();
            toolSpeed *= (1.0f + 0.2f * (amplifier + 1));
        }

        // Apply Mining Fatigue effect
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            int amplifier = player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier();
            toolSpeed *= Math.pow(0.3f, amplifier + 1);
        }

        // Minimum expected break time in ms:
        // vanilla formula: ticks = ceil(hardness * 30 / toolSpeed) for correct tool
        // 1 tick = 50ms, we use a generous multiplier to avoid false positives
        double minBreakMs = (hardness * 30.0 / toolSpeed) * 50.0
                * Praxic.getConfig().fastBreakSpeedMultiplier;

        if (elapsed < minBreakMs && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Block: %s | Hardness: %.1f | Elapsed: %dms | Min: %.0fms",
                            state.getBlock().getDescriptionId(), hardness, elapsed, minBreakMs));
        }
    }
}

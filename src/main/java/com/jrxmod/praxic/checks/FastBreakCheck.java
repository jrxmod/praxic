package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

        // Player dig speed, replicating Player#getDestroySpeed of vanilla 1.21.1.
        // ItemStack#getDestroySpeed already returns 1.0 when the held tool does
        // not match the block's material, which matches vanilla behaviour.
        ItemStack tool = player.getMainHandItem();
        float speed = tool.getDestroySpeed(state);
        if (speed < 1.0f) speed = 1.0f;

        // Efficiency contributes via the mining_efficiency attribute (normally
        // level^2 + 1) and only when the tool already grants a speed bonus.
        if (speed > 1.0f && !tool.isEmpty()) {
            speed += (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }

        // Haste / Conduit Power: +20% per level, the stronger effect applies.
        int hasteLevel = player.hasEffect(MobEffects.DIG_SPEED)
                ? player.getEffect(MobEffects.DIG_SPEED).getAmplifier() : -1;
        int conduitLevel = player.hasEffect(MobEffects.CONDUIT_POWER)
                ? player.getEffect(MobEffects.CONDUIT_POWER).getAmplifier() : -1;
        int effectLevel = Math.max(hasteLevel, conduitLevel);
        if (effectLevel >= 0) {
            speed *= 1.0f + 0.2f * (effectLevel + 1);
        }

        // Mining Fatigue: vanilla hardcoded multipliers per level.
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *= switch (player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }

        // block_break_speed attribute, normally 1.0.
        speed *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);

        // Underwater mining penalty: submerged_mining_speed attribute,
        // 0.2 by default, 1.0 with Aqua Affinity.
        if (player.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float) player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }

        // Mining while not standing on the ground is 5x slower.
        if (!player.onGround()) {
            speed *= 0.2f;
        }

        // Vanilla progress divisor: 30 when Player#hasCorrectToolForDrops is
        // true, 100 otherwise. That method is true either when the held tool is
        // correct for drops, OR when the block does not require a correct tool
        // at all (leaves, dirt, grass, logs break at the 30 divisor even with
        // bare hands). Mirroring it exactly is what keeps the expected time
        // aligned with the real server-side breaking speed.
        boolean canHarvest = player.hasCorrectToolForDrops(state);
        float divisor = canHarvest ? 30.0f : 100.0f;

        // Vanilla breaks the block instantly when per-tick progress reaches 1.0
        // (e.g. shears on leaves, Efficiency V + Haste on stone). Such breaks are
        // legitimate and must never be compared against a minimum duration.
        if (hardness * divisor / speed <= 1.0f) return;

        // Minimum expected break time in ms. Vanilla accumulates progress once
        // per tick (50 ms); the configured multiplier is a margin against
        // network jitter and blocks broken by several players at once.
        double minBreakMs = (hardness * divisor / speed) * 50.0
                * Praxic.getConfig().fastBreakSpeedMultiplier;

        if (elapsed < minBreakMs && data.canFlag(getName(), 2000)) {
            ViolationManager.flag(player, data, this,
                    String.format("Block: %s | Hardness: %.1f | Elapsed: %dms | Min: %.0fms",
                            state.getBlock().getDescriptionId(), hardness, elapsed, minBreakMs));
        }
    }
}

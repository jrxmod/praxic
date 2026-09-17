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

/**
 * Detects FastBreak - breaking blocks faster than vanilla allows.
 * Uses delay balance and speed balance plus rate check.
 * Vanilla has 6 tick (300ms) cooldown between non instant breaks.
 */
public class FastBreakCheck extends AbstractCheck {

    private static final long DELAY_THRESHOLD_MS = 200L;
    private static final long EXPECTED_DELAY_MS = 250L;
    private static final double DIFF_THRESHOLD_MS = 10.0;
    private static final double BALANCE_LIMIT = 400.0;
    private static final double DELAY_BALANCE_LIMIT = 300.0;
    private static final double DECAY = 0.85;
    private static final long RATE_WINDOW_MS = 1000L;
    private static final int RATE_MAX = 4;

    @Override
    public String getName() {
        return "FastBreakCheck";
    }

    private double getBlockDamage(ServerPlayer player, BlockState state, BlockPos pos) {
        try {
            float hardness = state.getDestroySpeed(player.serverLevel(), pos);
            if (hardness <= 0) return 1.0;
            if (hardness < 0) return 0;
            ItemStack tool = player.getMainHandItem();
            float speed = tool.getDestroySpeed(state);
            if (speed < 1.0f) speed = 1.0f;
            if (speed > 1.0f && !tool.isEmpty()) {
                speed += (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY);
            }
            int haste = player.hasEffect(MobEffects.DIG_SPEED) ? player.getEffect(MobEffects.DIG_SPEED).getAmplifier() : -1;
            int conduit = player.hasEffect(MobEffects.CONDUIT_POWER) ? player.getEffect(MobEffects.CONDUIT_POWER).getAmplifier() : -1;
            int effLevel = Math.max(haste, conduit);
            if (effLevel >= 0) speed *= 1.0f + 0.2f * (effLevel + 1);
            if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                int amp = player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier();
                speed *= switch (amp) { case 0 -> 0.3f; case 1 -> 0.09f; case 2 -> 0.0027f; default -> 0.00081f; };
            }
            speed *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
            if (player.isEyeInFluid(FluidTags.WATER)) speed *= (float) player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (!player.onGround()) speed *= 0.2f;
            boolean canHarvest = player.hasCorrectToolForDrops(state);
            float divisor = canHarvest ? 30.0f : 100.0f;
            return speed / hardness / divisor;
        } catch (Exception e) { return 0; }
    }

    public void onStartBreak(ServerPlayer player, BlockPos pos, PlayerData data) {
        long now = System.currentTimeMillis();
        data.clickTimestamps.addLast(now);
        while (!data.clickTimestamps.isEmpty() && now - data.clickTimestamps.peekFirst() > 1000L) data.clickTimestamps.pollFirst();

        if (!Praxic.getConfig().fastBreakCheckEnabled) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isSpectator()) return;
        if (data.joinGraceTicks > 0) return;

        data.breakTimes.addLast(now);
        while (!data.breakTimes.isEmpty() && now - data.breakTimes.peekFirst() > RATE_WINDOW_MS) data.breakTimes.pollFirst();

        if (data.lastFinishBreakTime > 0) {
            long delay = now - data.lastFinishBreakTime;
            if (delay < 100L) {
                if (data.canFlag(getName(), 300)) {
                    ViolationManager.flag(player, data, this,
                            String.format("No break delay: %dms expected %dms", delay, EXPECTED_DELAY_MS));
                }
                data.fastBreakDelayBalance += EXPECTED_DELAY_MS - delay;
            } else if (delay < DELAY_THRESHOLD_MS) {
                data.fastBreakDelayBalance += EXPECTED_DELAY_MS - delay;
                data.fastBreakBuffer++;
                if (data.fastBreakBuffer >= 1 && data.canFlag(getName(), 400)) {
                    ViolationManager.flag(player, data, this,
                            String.format("FastBreak delay: %dms bal %.1f", delay, data.fastBreakDelayBalance));
                    data.fastBreakBuffer = 0;
                }
            } else {
                data.fastBreakDelayBalance *= DECAY;
                data.fastBreakBuffer = Math.max(0, data.fastBreakBuffer - 1);
            }
            data.fastBreakDelayBalance = Math.max(-BALANCE_LIMIT, Math.min(BALANCE_LIMIT, data.fastBreakDelayBalance));
            if (data.fastBreakDelayBalance > DELAY_BALANCE_LIMIT && data.canFlag(getName(), 600)) {
                ViolationManager.flag(player, data, this,
                        String.format("Break delay bal %.1f delay %dms", data.fastBreakDelayBalance, delay));
                data.fastBreakDelayBalance = 0;
            }
        }

        data.breakStartTime = now;
        data.breakingBlockPos = pos;

        try {
            BlockState state = player.serverLevel().getBlockState(pos);
            float hardness = state.getDestroySpeed(player.serverLevel(), pos);
            data.breakingBlockHardness = hardness;
            double dmg = getBlockDamage(player, state, pos);
            data.breakingMaxDamage = dmg;
            if (hardness <= 0 || dmg >= 1.0) {
                data.breakingBlockInstaMine = true;
                data.breakingBlockMinMs = 0;
            } else {
                data.breakingBlockInstaMine = false;
                data.breakingBlockMinMs = Math.ceil(1.0 / dmg) * 50.0;
            }
        } catch (Exception e) {
            data.breakingBlockHardness = -1f;
            data.breakingBlockMinMs = -1;
            data.breakingMaxDamage = 0;
            data.breakingBlockInstaMine = false;
        }
    }

    public void onStopBreak(ServerPlayer player, BlockPos pos, PlayerData data) {
        if (!Praxic.getConfig().fastBreakCheckEnabled) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isSpectator()) return;
        if (data.joinGraceTicks > 0) return;

        long now = System.currentTimeMillis();
        if (data.breakStartTime == 0 || data.breakingBlockPos == null) return;
        if (!pos.equals(data.breakingBlockPos)) return;

        long elapsed = now - data.breakStartTime;
        double maxDmg = data.breakingMaxDamage;
        boolean insta = data.breakingBlockInstaMine;
        float hard = data.breakingBlockHardness;

        if (hard <= 0) return;
        if (insta) return;
        if (maxDmg <= 0) return;

        if (elapsed < 80 && hard > 0.2f && data.canFlag(getName(), 300)) {
            ViolationManager.flag(player, data, this,
                    String.format("FastBreak STOP instant: %dms hard %.1f pred %.0fms", elapsed, hard, Math.ceil(1.0 / maxDmg) * 50.0));
            return;
        }

        double predicted = Math.ceil(1.0 / maxDmg) * 50.0;
        if (predicted <= 0) return;

        if (elapsed < predicted * 0.75) {
            if (data.canFlag(getName(), 300)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastBreak STOP speed: %.0f%% of expected %dms < %.0fms hard %.1f", (elapsed / predicted * 100.0), elapsed, predicted, hard));
            }
        }
    }

    public void onAbortBreak(ServerPlayer player, PlayerData data) {
        data.breakStartTime = 0;
        data.breakingBlockPos = null;
        data.breakingBlockHardness = -1f;
        data.breakingBlockMinMs = -1;
        data.breakingMaxDamage = 0;
        data.breakingBlockInstaMine = false;
    }

    public void onBlockDestroyed(ServerPlayer player, BlockPos pos, PlayerData data) {
        if (!Praxic.getConfig().fastBreakCheckEnabled) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isSpectator()) return;

        long now = System.currentTimeMillis();

        if (data.breakTimes.size() > RATE_MAX) {
            if (data.breakingBlockHardness > 0.2f || data.breakTimes.size() > 5) {
                if (data.canFlag(getName(), 500)) {
                    ViolationManager.flag(player, data, this,
                            String.format("FastBreak rate: %d breaks in %dms max %d", data.breakTimes.size(), RATE_WINDOW_MS, RATE_MAX));
                }
            }
        }

        if (data.breakStartTime == 0 || data.breakingBlockPos == null) {
            if (data.canFlag(getName(), 300)) {
                ViolationManager.flag(player, data, this,
                        String.format("Instant break without START: %s", pos.toShortString()));
            }
            data.lastFinishBreakTime = now;
            return;
        }

        if (!pos.equals(data.breakingBlockPos)) {
            long elapsed = now - data.breakStartTime;
            if (elapsed < 100 && data.canFlag(getName(), 300)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastBreak switch %s -> %s %dms", data.breakingBlockPos.toShortString(), pos.toShortString(), elapsed));
            }
            data.breakStartTime = 0;
            data.breakingBlockPos = null;
            data.breakingBlockHardness = -1f;
            data.breakingBlockMinMs = -1;
            data.breakingMaxDamage = 0;
            data.breakingBlockInstaMine = false;
            data.lastFinishBreakTime = now;
            return;
        }

        long elapsed = now - data.breakStartTime;
        double maxDmg = data.breakingMaxDamage;
        boolean insta = data.breakingBlockInstaMine;
        float hard = data.breakingBlockHardness;

        data.breakStartTime = 0;
        data.breakingBlockPos = null;
        data.breakingBlockHardness = -1f;
        data.breakingBlockMinMs = -1;
        data.breakingMaxDamage = 0;
        data.breakingBlockInstaMine = false;
        data.lastFinishBreakTime = now;

        if (hard <= 0) return;
        if (insta) return;
        if (maxDmg <= 0) return;

        if (elapsed < 80 && hard > 0.2f && data.canFlag(getName(), 300)) {
            ViolationManager.flag(player, data, this,
                    String.format("FastBreak instant: %dms hard %.1f pred %.0fms", elapsed, hard, Math.ceil(1.0 / maxDmg) * 50.0));
            return;
        }

        double predicted = Math.ceil(1.0 / maxDmg) * 50.0;
        if (predicted <= 0) return;
        double diff = predicted - elapsed;

        if (diff < DIFF_THRESHOLD_MS) data.fastBreakBalance *= DECAY;
        else data.fastBreakBalance += diff;
        data.fastBreakBalance = Math.max(-BALANCE_LIMIT, Math.min(BALANCE_LIMIT, data.fastBreakBalance));

        if (data.fastBreakBalance > BALANCE_LIMIT && data.canFlag(getName(), 500)) {
            ViolationManager.flag(player, data, this,
                    String.format("FastBreak: hard %.1f dmg %.3f pred %.0fms real %dms diff %.0fms bal %.1f", hard, maxDmg, predicted, elapsed, diff, data.fastBreakBalance));
            data.fastBreakBalance = 0;
        }

        if (elapsed < predicted * 0.75) {
            if (data.canFlag(getName(), 300)) {
                ViolationManager.flag(player, data, this,
                        String.format("FastBreak speed: %.0f%% of expected %dms < %.0fms hard %.1f", (elapsed / predicted * 100.0), elapsed, predicted, hard));
            }
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastBreakCheckEnabled) return;

        if (data.breakStartTime != 0 && System.currentTimeMillis() - data.breakStartTime > 5000L) {
            data.breakStartTime = 0;
            data.breakingBlockPos = null;
            data.breakingBlockHardness = -1f;
            data.breakingBlockMinMs = -1;
            data.breakingMaxDamage = 0;
            data.breakingBlockInstaMine = false;
        }

        if (data.breakStartTime != 0 && data.breakingBlockPos != null) {
            try {
                BlockState state = player.serverLevel().getBlockState(data.breakingBlockPos);
                double cur = getBlockDamage(player, state, data.breakingBlockPos);
                if (cur > data.breakingMaxDamage) {
                    data.breakingMaxDamage = cur;
                    if (cur >= 1.0) data.breakingBlockInstaMine = true;
                    else data.breakingBlockMinMs = Math.ceil(1.0 / cur) * 50.0;
                }
            } catch (Exception ignored) {}
        }

        if (data.breakStartTime == 0) {
            if (Math.abs(data.fastBreakBalance) > 0.1) data.fastBreakBalance *= 0.985;
            if (Math.abs(data.fastBreakDelayBalance) > 0.1) data.fastBreakDelayBalance *= 0.985;
        }

        long now = System.currentTimeMillis();
        while (!data.breakTimes.isEmpty() && now - data.breakTimes.peekFirst() > RATE_WINDOW_MS) data.breakTimes.pollFirst();
    }
}

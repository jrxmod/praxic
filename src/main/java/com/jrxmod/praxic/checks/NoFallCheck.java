package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fall length is peakY minus current Y. Client onGround / fallDistance are
 * ignored: NoFall sends StatusOnly(onGround=true) which zeroes both.
 */
public class NoFallCheck extends AbstractCheck {

    private static final double MIN_FALL = 4.0;
    private static final int SPOOF_THRESHOLD = 3;

    @Override
    public String getName() {
        return "NoFallCheck";
    }

    public void onMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet, PlayerData data) {
        if (!Praxic.getConfig().noFallCheckEnabled) return;
        if (exempt(player)) {
            resetFall(data, player.getY());
            return;
        }
        if (data.joinGraceTicks > 0) return;

        double x = packet.hasPosition() ? packet.getX(player.getX()) : player.getX();
        double y = packet.hasPosition() ? packet.getY(player.getY()) : player.getY();
        double z = packet.hasPosition() ? packet.getZ(player.getZ()) : player.getZ();
        tickFall(player, data, x, y, z, packet.isOnGround(), true);
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().noFallCheckEnabled) return;
        if (exempt(player)) {
            resetFall(data, player.getY());
            return;
        }

        if (data.pendingFallCheck) {
            data.pendingFallCheck = false;
            float healthNow = player.getHealth() + player.getAbsorptionAmount();
            float healthBefore = data.totalHealthBeforeLanding;
            double fallDist = data.pendingFallDistance;
            if (healthBefore > 0 && fallDist >= MIN_FALL && data.canFlag(getName(), 2500)
                    && !isOnSafeLandingBlock(player, data.pendingFallPos)
                    && healthNow > healthBefore - 0.5f) {
                ViolationManager.flag(player, data, this,
                        String.format("No fall damage after %.2f block drop (hp %.1f -> %.1f)",
                                fallDist, healthBefore, healthNow));
            }
            data.totalHealthBeforeLanding = -1;
            data.pendingFallDistance = 0;
            data.pendingFallPos = null;
        }

        tickFall(player, data, player.getX(), player.getY(), player.getZ(), false, false);
    }

    /**
     * @param countSpoof true for move packets (onGround bit is meaningful)
     */
    private void tickFall(ServerPlayer player, PlayerData data,
                          double x, double y, double z,
                          boolean packetOnGround, boolean countSpoof) {
        if (player.isInWater() || player.isInLava() || player.onClimbable()) {
            resetFall(data, y);
            return;
        }

        if (!data.noFallYSet) {
            data.noFallPeakY = y;
            data.noFallLastY = y;
            data.noFallYSet = true;
        }
        if (y > data.noFallPeakY) {
            data.noFallPeakY = y;
        }
        double fall = data.noFallPeakY - y;
        data.maxFallDistance = fall;
        data.noFallLastY = y;

        boolean support = standingOnBlock(player, x, y, z);

        if (!support) {
            data.wasInAir = true;
            if (fall >= MIN_FALL && data.totalHealthBeforeLanding < 0) {
                data.totalHealthBeforeLanding = player.getHealth() + player.getAbsorptionAmount();
            }
            if (countSpoof && packetOnGround && fall >= MIN_FALL) {
                if (liquidBelow(player, x, y, z, 4)) {
                    data.noFallSpoofTicks = 0;
                } else {
                    data.noFallSpoofTicks++;
                    if (data.noFallSpoofTicks >= SPOOF_THRESHOLD && data.canFlag(getName(), 2500)) {
                        ViolationManager.flag(player, data, this,
                                String.format("onGround spoof in air after %.2f block drop", fall));
                    }
                }
            }
            return;
        }

        if (data.wasInAir && fall >= MIN_FALL) {
            if (data.totalHealthBeforeLanding < 0) {
                data.totalHealthBeforeLanding = player.getHealth() + player.getAbsorptionAmount();
            }
            data.pendingFallCheck = true;
            data.pendingFallDistance = fall;
            data.pendingFallPos = BlockPos.containing(x, y, z);
        }
        data.wasInAir = false;
        data.noFallPeakY = y;
        data.maxFallDistance = 0;
        data.noFallSpoofTicks = 0;
    }

    private static boolean exempt(ServerPlayer player) {
        if (!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE)) return true;
        if (player.isSpectator()) return true;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return true;
        if (player.isDeadOrDying() || player.isPassenger()) return true;
        if (player.isFallFlying() || player.getAbilities().flying || player.getAbilities().mayfly) return true;
        return player.hasEffect(MobEffects.SLOW_FALLING);
    }

    /**
     * True only when the block at the feet (not one block below) has collision
     * or fluid. Checking below() treats the last block of a fall as already landed.
     */
    private static boolean standingOnBlock(ServerPlayer player, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y - 0.07, z);
        if (!player.level().getFluidState(feet).isEmpty()) return true;
        BlockState state = player.level().getBlockState(feet);
        return !state.getCollisionShape(player.level(), feet).isEmpty();
    }

    private static boolean liquidBelow(ServerPlayer player, double x, double y, double z, int depth) {
        for (int i = 0; i <= depth; i++) {
            BlockPos pos = BlockPos.containing(x, y - i, z);
            if (!player.level().getFluidState(pos).isEmpty()) return true;
        }
        return false;
    }

    private static boolean isOnSafeLandingBlock(ServerPlayer player, BlockPos landingPos) {
        if (landingPos == null) return false;
        BlockPos pos = landingPos;
        var level = player.level();
        for (int i = 0; i < 2; i++) {
            var state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block == net.minecraft.world.level.block.Blocks.HAY_BLOCK
                    || block == net.minecraft.world.level.block.Blocks.SLIME_BLOCK
                    || block == net.minecraft.world.level.block.Blocks.HONEY_BLOCK
                    || block == net.minecraft.world.level.block.Blocks.COBWEB
                    || block == net.minecraft.world.level.block.Blocks.POWDER_SNOW
                    || block == net.minecraft.world.level.block.Blocks.SCAFFOLDING
                    || block instanceof SweetBerryBushBlock
                    || state.is(net.minecraft.tags.BlockTags.BEDS)
                    || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
                    || state.is(net.minecraft.tags.BlockTags.WOOL)) {
                return true;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (id.contains("moss") || id.contains("honeycomb")) return true;
            pos = pos.below();
        }
        return false;
    }

    private static void resetFall(PlayerData data, double y) {
        data.maxFallDistance = 0;
        data.totalHealthBeforeLanding = -1;
        data.wasInAir = false;
        data.pendingFallCheck = false;
        data.pendingFallDistance = 0;
        data.pendingFallPos = null;
        data.noFallSpoofTicks = 0;
        data.noFallPeakY = y;
        data.noFallLastY = y;
        data.noFallYSet = true;
    }
}

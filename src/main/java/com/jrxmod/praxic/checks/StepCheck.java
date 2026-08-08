package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Detects Step hacks — instant vertical climb over 0.6 blocks without jumping.
 * Vanilla max step is 0.6; anything higher requires jumping or ledge.
 */
public class StepCheck extends AbstractCheck {

    private static final double VANILLA_STEP = 0.6;
    private static final double STEP_BUFFER_MULT = 0.15;
    private static final int BUFFER_THRESHOLD = 2;

    @Override
    public String getName() {
        return "StepCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().stepCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.onClimbable()) return;
        if (player.isFallFlying()) return;
        if (data.joinGraceTicks > 0) return;
        if (player.hasEffect(MobEffects.JUMP)) return;
        if (player.hasEffect(MobEffects.LEVITATION)) return;

        double dy = player.getY() - data.prevY;
        if (dy <= VANILLA_STEP) {
            data.stepBuffer = Math.max(0, data.stepBuffer - 1);
            return;
        }

        // Must be from ground — if already airborne, vertical can exceed step via jump physics
        if (data.prevMovementState != MovementState.GROUND && data.prevMovementState != MovementState.JUMP) {
            data.stepBuffer = Math.max(0, data.stepBuffer - 1);
            return;
        }

        // Skip if block above is climbable or if stepping onto slabs/stairs legitimately can cause some jitter
        BlockPos below = player.blockPosition().below();
        var stateBelow = player.level().getBlockState(below);
        if (stateBelow.is(Blocks.SLIME_BLOCK) || stateBelow.is(Blocks.HONEY_BLOCK)) {
            data.stepBuffer = 0;
            return;
        }

        double maxStep = Praxic.getConfig().stepMaxHeight > 0 ? Praxic.getConfig().stepMaxHeight : VANILLA_STEP + STEP_BUFFER_MULT;

        if (dy > maxStep) {
            data.stepBuffer++;
        }

        if (data.stepBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Step height %.3f (max %.2f) buffer %d", dy, maxStep, data.stepBuffer));
            data.stepBuffer = 0;
        }
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

public class NoFallCheck extends AbstractCheck {

    // Minimum fall distance to expect damage
    private static final double MIN_FALL_DISTANCE = 3.5;

    @Override
    public String getName() {
        return "NoFallCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {

        if (!Praxic.getConfig().noFallCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (player.isPassenger()) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) return;
        if (player.hasEffect(MobEffects.JUMP)) return;
        if (player.isFallFlying()) return;
        if (player.getAbilities().flying) return;

        // Tick N+1: verify fall damage was actually taken
        if (data.pendingFallCheck) {
            data.pendingFallCheck = false;

            float healthNow = player.getHealth();
            float healthBefore = data.healthBeforeLanding;

            if (healthBefore > 0 && healthNow >= healthBefore && data.canFlag(getName(), 3000)) {
                if (!isOnSafeLandingBlock(player)) {
                    ViolationManager.flag(player, data, this,
                            String.format("No fall damage taken, fall: %.2f blocks, health: %.1f -> %.1f",
                                    data.pendingFallDistance, healthBefore, healthNow));
                }
            }

            resetFallData(data);
            return;
        }

        boolean onGround = player.onGround();

        if (!onGround) {
            // Track max fall distance while in air
            float fallDistance = player.fallDistance;
            if (fallDistance > data.maxFallDistance) {
                data.maxFallDistance = fallDistance;
            }

            // Snapshot health before expected landing
            if (data.maxFallDistance >= MIN_FALL_DISTANCE) {
                data.healthBeforeLanding = player.getHealth();
            }

            data.wasInAir = true;
            return;
        }

        // Player just landed — schedule check for next tick so damage can be applied
        if (data.wasInAir && data.maxFallDistance >= MIN_FALL_DISTANCE) {
            data.pendingFallCheck = true;
            data.pendingFallDistance = data.maxFallDistance;
            data.wasInAir = false;
            data.maxFallDistance = 0;
            return;
        }

        resetFallData(data);
    }

    private void resetFallData(PlayerData data) {
        data.maxFallDistance = 0;
        data.healthBeforeLanding = -1;
        data.wasInAir = false;
        data.pendingFallCheck = false;
        data.pendingFallDistance = 0;
    }

    private boolean isOnSafeLandingBlock(ServerPlayer player) {
        BlockPos pos = player.blockPosition().below();
        Block block = player.level().getBlockState(pos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).getPath();

        return blockId.contains("hay") ||
               blockId.contains("slime") ||
               blockId.contains("bed") ||
               blockId.contains("powder_snow") ||
               blockId.contains("honeycomb") ||
               blockId.contains("web");
    }
}

package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;

public class NoFallCheck extends AbstractCheck {

    private static final double MIN_FALL_DISTANCE = 3.5;
    private static final double FEATHER_FALLING_BUFFER_PER_LEVEL = 1.0;

    private static final ResourceKey<Enchantment> FEATHER_FALLING_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.withDefaultNamespace("feather_falling")
    );

    @Override
    public String getName() {
        return "NoFallCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().noFallCheckEnabled) return;

        if (player.isSpectator() || player.isCreative() || player.isDeadOrDying() || 
            player.isPassenger() || player.isInWater() || player.isInLava() ||
            player.hasEffect(MobEffects.SLOW_FALLING) || player.hasEffect(MobEffects.JUMP) ||
            player.isFallFlying() || player.getAbilities().flying || player.onClimbable()) {
            resetFallData(data);
            return;
        }

        if (data.pendingFallCheck) {
            data.pendingFallCheck = false;
            float healthNow = player.getHealth() + player.getAbsorptionAmount();
            float healthBefore = data.totalHealthBeforeLanding;

            if (healthBefore > 0 && healthNow >= healthBefore && data.canFlag(getName(), 3000)) {
                if (!isOnSafeLandingBlock(player)) {
                    ViolationManager.flag(player, data, this,
                            String.format("No fall damage taken, fall: %.2f blocks, total health: %.1f -> %.1f",
                                    data.pendingFallDistance, healthBefore, healthNow));
                }
            }
            resetFallData(data);
            return;
        }

        if (!player.onGround()) {
            float fallDistance = player.fallDistance;
            if (fallDistance > data.maxFallDistance) data.maxFallDistance = fallDistance;

            double effectiveMinFall = MIN_FALL_DISTANCE + getFeatherFallingLevel(player) * FEATHER_FALLING_BUFFER_PER_LEVEL;
            if (data.maxFallDistance >= effectiveMinFall) {
                data.totalHealthBeforeLanding = player.getHealth() + player.getAbsorptionAmount();
            }
            data.wasInAir = true;
        } else if (data.wasInAir) {
            double effectiveMinFall = MIN_FALL_DISTANCE + getFeatherFallingLevel(player) * FEATHER_FALLING_BUFFER_PER_LEVEL;
            if (data.maxFallDistance >= effectiveMinFall) {
                data.pendingFallCheck = true;
                data.pendingFallDistance = data.maxFallDistance;
            }
            data.wasInAir = false;
            data.maxFallDistance = 0;
        }
    }

    private int getFeatherFallingLevel(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return 0;
        ItemEnchantments enchantments = boots.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return 0;
        var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return registry.get(FEATHER_FALLING_KEY).map(enchantments::getLevel).orElse(0);
    }

    private void resetFallData(PlayerData data) {
        data.maxFallDistance = 0;
        data.totalHealthBeforeLanding = -1;
        data.wasInAir = false;
        data.pendingFallCheck = false;
        data.pendingFallDistance = 0;
    }

    private boolean isOnSafeLandingBlock(ServerPlayer player) {
        BlockPos pos = player.blockPosition().below();
        Block block = player.level().getBlockState(pos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return blockId.contains("hay") || blockId.contains("slime") || blockId.contains("bed") ||
               blockId.contains("powder_snow") || blockId.contains("honeycomb") || blockId.contains("web");
    }
}

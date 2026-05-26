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

    // Minimum fall distance to start tracking
    private static final double MIN_FALL_DISTANCE = 6.0;

    // Feather Falling adds 2 blocks of buffer per enchantment level
    private static final double FEATHER_FALLING_BUFFER_PER_LEVEL = 2.0;

    // HP buffer above expected post-damage health before flagging
    private static final float DAMAGE_BUFFER = 3.0f;

    /**
     * If player.fallDistance drops to less than this fraction of our tracked max
     * while still airborne, the fall was interrupted (vine, climbable, water).
     * Reset tracker to avoid flagging on the remainder of the fall.
     */
    private static final float FALL_INTERRUPT_RATIO = 0.5f;

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

        // ── Pending check: evaluate damage one tick after landing ────────────
        if (data.pendingFallCheck) {
            data.pendingFallCheck = false;
            float healthNow    = player.getHealth() + player.getAbsorptionAmount();
            float healthBefore = data.totalHealthBeforeLanding;
            double fallDist    = data.pendingFallDistance;

            if (healthBefore > 0 && data.canFlag(getName(), 3000) && !isOnSafeLandingBlock(player)) {
                float expectedDamage    = (float) Math.max(1.0, fallDist - 3.0);
                float expectedHealthMin = healthBefore - expectedDamage;

                if (healthNow > expectedHealthMin + DAMAGE_BUFFER) {
                    ViolationManager.flag(player, data, this,
                            String.format("Suppressed fall damage: fall=%.2f blocks, " +
                                    "expected HP=%.1f actual HP=%.1f (before=%.1f)",
                                    fallDist, expectedHealthMin, healthNow, healthBefore));
                }
            }
            resetFallData(data);
            return;
        }

        // ── Track fall distance and snapshot health while airborne ───────────
        if (!player.onGround()) {
            float fallDistance = player.fallDistance;

            // Detect interrupted fall: vine, ladder, climbable, water exit, etc.
            // If server-side fallDistance dropped significantly below our tracked max,
            // the fall was broken mid-air — reset tracker to avoid false positives.
            if (data.wasInAir
                    && data.maxFallDistance > MIN_FALL_DISTANCE
                    && fallDistance < data.maxFallDistance * FALL_INTERRUPT_RATIO) {
                data.maxFallDistance          = fallDistance;
                data.totalHealthBeforeLanding = -1;
            }

            if (fallDistance > data.maxFallDistance) {
                data.maxFallDistance = fallDistance;
            }

            double effectiveMinFall = MIN_FALL_DISTANCE
                    + getFeatherFallingLevel(player) * FEATHER_FALLING_BUFFER_PER_LEVEL;

            if (data.maxFallDistance >= effectiveMinFall) {
                // Snapshot health once when threshold is first crossed
                if (data.totalHealthBeforeLanding < 0) {
                    data.totalHealthBeforeLanding = player.getHealth() + player.getAbsorptionAmount();
                }
            }
            data.wasInAir = true;
        } else if (data.wasInAir) {
            // Player just landed
            double effectiveMinFall = MIN_FALL_DISTANCE
                    + getFeatherFallingLevel(player) * FEATHER_FALLING_BUFFER_PER_LEVEL;

            if (data.maxFallDistance >= effectiveMinFall && data.totalHealthBeforeLanding > 0) {
                data.pendingFallCheck    = true;
                data.pendingFallDistance = data.maxFallDistance;
            }
            data.wasInAir        = false;
            data.maxFallDistance = 0;
        }
    }

    // ── Enchantment helpers ──────────────────────────────────────────────────

    private int getFeatherFallingLevel(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return 0;
        ItemEnchantments enchantments = boots.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return 0;
        var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return registry.get(FEATHER_FALLING_KEY).map(enchantments::getLevel).orElse(0);
    }

    // ── Safe block detection ─────────────────────────────────────────────────

    private boolean isOnSafeLandingBlock(ServerPlayer player) {
        BlockPos pos = player.blockPosition().below();
        Block block = player.level().getBlockState(pos).getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return id.contains("hay")         ||
               id.contains("slime")       ||
               id.contains("bed")         ||
               id.contains("powder_snow") ||
               id.contains("honeycomb")   ||
               id.contains("web")         ||
               id.contains("carpet")      ||
               id.contains("moss");
    }

    // ── Reset helper ─────────────────────────────────────────────────────────

    private void resetFallData(PlayerData data) {
        data.maxFallDistance          = 0;
        data.totalHealthBeforeLanding = -1;
        data.wasInAir                 = false;
        data.pendingFallCheck         = false;
        data.pendingFallDistance      = 0;
    }
}

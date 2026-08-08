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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SweetBerryBushBlock;

public class NoFallCheck extends AbstractCheck {

    // Minimum fall distance to start tracking
    private static final double MIN_FALL_DISTANCE = 6.0;

    // Feather Falling adds 2 blocks of buffer per enchantment level
    private static final double FEATHER_FALLING_BUFFER_PER_LEVEL = 2.0;

    /**
     * Fall damage is considered suppressed when less than half of the expected
     * damage (after armor, enchantment and effect reductions) was actually dealt.
     */
    private static final double SUPPRESSION_RATIO = 0.5;

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

        // Servers may disable fall damage globally via the fallDamage gamerule.
        if (!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE)) {
            resetFallData(data);
            return;
        }

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

            if (healthBefore > 0 && data.canFlag(getName(), 3000)
                    && !isOnSafeLandingBlock(player, data.pendingFallPos)) {
                float expectedDamage = expectedFallDamage(player, fallDist);

                // Flag only when the dealt damage is clearly below the expected
                // amount. Vanilla reductions are accounted for beforehand, so a
                // legitimate landing keeps health within the expected range.
                if (expectedDamage >= 1.0f
                        && healthNow > healthBefore - expectedDamage * (float) SUPPRESSION_RATIO) {
                    ViolationManager.flag(player, data, this,
                            String.format("Suppressed fall damage: fall=%.2f blocks, " +
                                    "expected HP=%.1f actual HP=%.1f (before=%.1f)",
                                    fallDist, healthBefore - expectedDamage, healthNow, healthBefore));
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
                data.pendingFallPos      = player.blockPosition();
            }
            data.wasInAir        = false;
            data.maxFallDistance = 0;
        }
    }

    /**
     * Computes the damage vanilla would deal for the given fall distance,
     * applying armor, protection enchantments (Protection / Feather Falling)
     * and the Resistance effect.
     */
    private float expectedFallDamage(ServerPlayer player, double fallDist) {
        // Vanilla damage: fall distance minus 3, rounded up.
        float damage = (float) Math.ceil(fallDist - 3.0);
        if (damage < 1.0f) return 0.0f;

        // Armor reduces fall damage in vanilla: 4% per armor point, capped at 80%.
        float armorReduction = Math.min(20.0f, player.getArmorValue()) / 25.0f;
        damage *= 1.0f - armorReduction;

        // Protection and Feather Falling points from the vanilla protection system.
        DamageSource fallSource = player.damageSources().fall();
        float protection = EnchantmentHelper.getDamageProtection(
                player.serverLevel(), player, fallSource);
        float protectionReduction = Math.min(20.0f, protection) / 25.0f;
        damage *= 1.0f - protectionReduction;

        // Resistance reduces all damage by 20% per level.
        if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            int amplifier = player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier();
            damage *= Math.max(0.0f, 1.0f - 0.2f * (amplifier + 1));
        }

        return damage;
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

    /**
     * Checks the block the player landed on (and blocks below it) for vanilla
     * blocks that reduce or negate fall damage: slime, honey, hay, cobweb,
     * powder snow, scaffolding, beds, wool carpets, wool and sweet berry bushes.
     */
    private boolean isOnSafeLandingBlock(ServerPlayer player, BlockPos landingPos) {
        if (landingPos == null) return false;
        BlockPos pos = landingPos.below();
        var level = player.level();
        // Check 2 blocks below as well because player eye height can shift
        for (int i = 0; i < 2; i++) {
            var state = level.getBlockState(pos);
            Block block = state.getBlock();
            // Exact checks for common safe landing blocks
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
            // Moss and honeycomb via string fallback for mod compat
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (id.contains("moss") || id.contains("honeycomb")) return true;
            pos = pos.below();
        }
        return false;
    }

    // ── Reset helper ─────────────────────────────────────────────────────────

    private void resetFallData(PlayerData data) {
        data.maxFallDistance          = 0;
        data.totalHealthBeforeLanding = -1;
        data.wasInAir                 = false;
        data.pendingFallCheck         = false;
        data.pendingFallDistance      = 0;
        data.pendingFallPos           = null;
    }
}

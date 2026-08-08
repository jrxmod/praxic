package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.FastPlaceCheck;
import com.jrxmod.praxic.checks.ScaffoldCheck;
import com.jrxmod.praxic.checks.TowerCheck;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts successful block placements for FastPlaceCheck, ScaffoldCheck and
 * TowerCheck. Fires only when the used item is a block item and the
 * interaction consumed the click, so non-block items (fireworks, food, tools)
 * and failed placement attempts are ignored.
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void praxic$onUseItemOn(ServerPlayer player, Level level, ItemStack stack,
                                    InteractionHand hand, BlockHitResult hitResult,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (!(stack.getItem() instanceof BlockItem)) return;
        if (!cir.getReturnValue().consumesAction()) return;

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        if (data == null) return;

        for (var check : Praxic.getCheckManager().getChecks()) {
            if (check instanceof FastPlaceCheck fastPlace) {
                fastPlace.onBlockPlace(player, data);
            } else if (check instanceof ScaffoldCheck scaffold) {
                scaffold.onBlockPlace(player, hitResult.getBlockPos(), data);
            } else if (check instanceof TowerCheck tower) {
                tower.onBlockPlace(player, hitResult.getBlockPos(), data);
            }
        }
    }
}

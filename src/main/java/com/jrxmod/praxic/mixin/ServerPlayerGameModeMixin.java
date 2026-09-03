package com.jrxmod.praxic.mixin;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.CheckManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts successful block placements for FastPlaceCheck, ScaffoldCheck and
 * TowerCheck. Fires only when the used item is a block item and the
 * interaction consumed the click, so non-block items (fireworks, food, tools)
 * and failed placement attempts are ignored.
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    /**
     * Vanilla destroys a block only through destroyAndAck: insta-mine, a
     * STOP_DESTROY_BLOCK with completed progress, or a delayed destroy. The
     * packet-only STOP handler in ServerGamePacketListenerMixin also fires for
     * cancels, block switches and other players' breaks, so FastBreakCheck is
     * evaluated here, on the real destruction path.
     */
    @Inject(method = "destroyAndAck", at = @At("HEAD"))
    private void praxic$onBlockDestroyed(BlockPos pos, int sequence, String message, CallbackInfo ci) {
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        cm.getFastBreakCheck().onBlockDestroyed(player, pos, data);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void praxic$mitigateUseItemOn(ServerPlayer player, Level level, ItemStack stack,
                                          InteractionHand hand, BlockHitResult hitResult,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (!(stack.getItem() instanceof BlockItem)) return;
        CheckManager cm = Praxic.getCheckManager();
        if (cm == null) return;
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;
        if (cm.getAirPlaceCheck().shouldCancel(player, hitResult, data)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void praxic$onUseItemOn(ServerPlayer player, Level level, ItemStack stack,
                                    InteractionHand hand, BlockHitResult hitResult,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (!(stack.getItem() instanceof BlockItem)) return;
        if (!cir.getReturnValue().consumesAction()) return;

        CheckManager cm = Praxic.getCheckManager();
        PlayerData data = cm.getPlayerData(player.getUUID());
        if (data == null) return;

        BlockPos placed = hitResult.getBlockPos().relative(hitResult.getDirection());
        cm.getFastPlaceCheck().onBlockPlace(player, data);
        cm.getScaffoldCheck().onBlockPlace(player, placed, data);
        cm.getTowerCheck().onBlockPlace(player, placed, data);
        cm.getAirPlaceCheck().onBlockPlace(player, hitResult, data);
    }
}

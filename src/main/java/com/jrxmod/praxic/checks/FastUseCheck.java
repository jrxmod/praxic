package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;

/**
 * Detects FastEat / FastUse: finishing an eat or drink animation in far fewer
 * ticks than vanilla allows. Vanilla food takes 32 ticks (1.6 s). Cheats that
 * skip the use animation complete in a handful of ticks.
 *
 * Bows, tridents, shields and crossbows can be released at any time and are
 * ignored. Only EAT and DRINK use animations are evaluated.
 */
public class FastUseCheck extends AbstractCheck {

    /** Vanilla eat/drink duration is 32 ticks; flag well below that. */
    private static final int MIN_LEGIT_TICKS = 12;
    private static final int BUFFER_THRESHOLD = 2;

    @Override
    public String getName() {
        return "FastUseCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastUseCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        boolean using = player.isUsingItem();
        boolean consumable = false;
        if (using) {
            UseAnim anim = player.getUseItem().getUseAnimation();
            consumable = anim == UseAnim.EAT || anim == UseAnim.DRINK;
        }

        if (using && consumable) {
            data.fastUseTicks++;
            data.fastUseWasConsumable = true;
            data.wasUsingItem = true;
            return;
        }

        // A use that ends without vanilla completeUsingItem() is a cancel
        // (button released early, item switched, death, ...). FastUseCheck
        // flags only when completeUsingItem() is actually reached, see
        // onUseCompleted().
        data.fastUseTicks = 0;
        data.fastUseWasConsumable = false;
        data.wasUsingItem = using;
    }

    /**
     * Called from LivingEntity#completeUsingItem, which vanilla reaches only
     * when the use timer ran out naturally. Interrupted uses (early release,
     * item switch, attack while eating) do not reach it and are legitimate.
     */
    public void onUseCompleted(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().fastUseCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;

        if (!data.fastUseWasConsumable || data.fastUseTicks <= 0) return;

        if (data.fastUseTicks < MIN_LEGIT_TICKS) {
            data.fastUseBuffer++;
            if (data.fastUseBuffer >= BUFFER_THRESHOLD && data.canFlag(getName(), 2500)) {
                ViolationManager.flag(player, data, this,
                        String.format("Finished consumable in %d ticks (min %d)",
                                data.fastUseTicks, MIN_LEGIT_TICKS));
                data.fastUseBuffer = 0;
            }
        } else {
            data.fastUseBuffer = Math.max(0, data.fastUseBuffer - 1);
        }

        data.fastUseTicks = 0;
        data.fastUseWasConsumable = false;
    }
}

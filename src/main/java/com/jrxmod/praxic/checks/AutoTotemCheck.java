package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class AutoTotemCheck extends AbstractCheck {

    /**
     * Instant auto-equip is one client tick (~50ms). 400ms still beats a human
     * opening inventory after a pop.
     */
    private static final long MIN_REEQUIP_MS = 400;

    private static final long TOTEM_TRACKING_TIMEOUT_MS = 2000;

    /** Vanilla offhand slot in the player inventory menu. */
    private static final int OFFHAND_SLOT = 45;

    @Override
    public String getName() {
        return "AutoTotemCheck";
    }

    public void onTotemPop(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().autoTotemCheckEnabled) return;
        data.lastTotemUseTime = System.currentTimeMillis();
        data.lastDamageTime = data.lastTotemUseTime;
        data.hadTotemInHand = false;
    }

    public void onDamage(ServerPlayer player, PlayerData data) {
        data.lastDamageTime = System.currentTimeMillis();
    }

    public void onContainerClick(ServerPlayer player, PlayerData data,
                                 ServerboundContainerClickPacket packet) {
        if (!Praxic.getConfig().autoTotemCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        long now = System.currentTimeMillis();
        boolean popWindow = data.lastTotemUseTime > 0 && now - data.lastTotemUseTime < MIN_REEQUIP_MS;
        boolean hurtWindow = (player.hurtTime > 0 || now - data.lastDamageTime < 1000L)
                && player.getOffhandItem().isEmpty();
        if (!popWindow && !hurtWindow) return;

        int slot = packet.getSlotNum();
        boolean totemClick = slot == OFFHAND_SLOT
                || packet.getCarriedItem().is(Items.TOTEM_OF_UNDYING)
                || packet.getClickType() == ClickType.SWAP && slot == OFFHAND_SLOT;
        if (!totemClick && slot < 0) return;
        if (!totemClick && !popWindow) return;

        long start = data.lastTotemUseTime > 0 ? data.lastTotemUseTime : data.lastDamageTime;
        long delta = now - start;
        if (delta < MIN_REEQUIP_MS && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("AutoTotem: click slot=%d %dms after pop/damage (min: %dms)",
                            slot, delta, MIN_REEQUIP_MS));
            data.lastTotemUseTime = 0;
        }
    }

    public void onOffhandSwap(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().autoTotemCheckEnabled) return;
        if (data.lastTotemUseTime <= 0 && data.lastDamageTime <= 0) return;
        long now = System.currentTimeMillis();
        long start = data.lastTotemUseTime > 0 ? data.lastTotemUseTime : data.lastDamageTime;
        long delta = now - start;
        if (delta < MIN_REEQUIP_MS && player.getOffhandItem().isEmpty() && data.canFlag(getName(), 3000)) {
            ViolationManager.flag(player, data, this,
                    String.format("AutoTotem: offhand swap %dms after pop/damage (min: %dms)",
                            delta, MIN_REEQUIP_MS));
            data.lastTotemUseTime = 0;
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().autoTotemCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;

        boolean hasTotem = hasTotemInHand(player);
        long now = System.currentTimeMillis();

        if (data.hadTotemInHand && !hasTotem) {
            if (now - data.lastDamageTime < 2000L) {
                if (data.lastTotemUseTime == 0) {
                    data.lastTotemUseTime = now;
                }
            }
        }

        if (!data.hadTotemInHand && hasTotem && data.lastTotemUseTime > 0) {
            long delta = now - data.lastTotemUseTime;
            if (delta < MIN_REEQUIP_MS && data.canFlag(getName(), 3000)) {
                ViolationManager.flag(player, data, this,
                        String.format("AutoTotem: re-equipped in %dms (min: %dms)", delta, MIN_REEQUIP_MS));
            }
            data.lastTotemUseTime = 0;
        }

        if (data.lastTotemUseTime > 0 && now - data.lastTotemUseTime > TOTEM_TRACKING_TIMEOUT_MS) {
            data.lastTotemUseTime = 0;
        }

        data.hadTotemInHand = hasTotem;
    }

    private boolean hasTotemInHand(ServerPlayer player) {
        return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }
}

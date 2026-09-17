package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Detects automatic armor equipping.
 * Flags fast multiple equips and instant equip after damage.
 */
public class AutoArmorCheck extends AbstractCheck {

    private static final long WINDOW_SHORT_MS = 300L;
    private static final long WINDOW_LONG_MS = 750L;
    private static final long AFTER_DAMAGE_MS = 800L;
    private static final int MAX_SHORT = 3;
    private static final int MAX_LONG = 4;

    @Override
    public String getName() {
        return "AutoArmorCheck";
    }

    public void onInventoryClick(ServerPlayer player, PlayerData data, AbstractContainerMenu menu, int slotIndex, ItemStack carried, ItemStack clicked) {
        if (!Praxic.getConfig().autoArmorCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.isDeadOrDying()) return;
        if (data.joinGraceTicks > 0) return;

        if (slotIndex < 5 || slotIndex > 8) return;
        if (carried.isEmpty() || !(carried.getItem() instanceof ArmorItem)) return;

        long now = System.currentTimeMillis();
        data.armorEquipTimes.addLast(now);
        data.lastArmorEquipTime = now;
        while (!data.armorEquipTimes.isEmpty() && now - data.armorEquipTimes.peekFirst() > WINDOW_LONG_MS) data.armorEquipTimes.pollFirst();

        int equips = data.armorEquipTimes.size();
        long oldest = data.armorEquipTimes.peekFirst() != null ? data.armorEquipTimes.peekFirst() : now;
        long span = now - oldest;

        boolean afterDamage = (now - data.lastDamageTime) <= AFTER_DAMAGE_MS;

        boolean shouldFlag = false;
        if (equips >= MAX_SHORT && span <= WINDOW_SHORT_MS) shouldFlag = true;
        else if (equips >= MAX_LONG && span <= WINDOW_LONG_MS) shouldFlag = true;
        else if (afterDamage && equips >= 2 && span <= 500L) shouldFlag = true;
        else if (afterDamage && equips >= 1 && span <= 200L) shouldFlag = true;

        if (shouldFlag && data.canFlag(getName(), 1500)) {
            ViolationManager.flag(player, data, this,
                    String.format("Armor equips: %d in %dms afterDmg %s", equips, span, afterDamage));
            data.armorEquipTimes.clear();
        }
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().autoArmorCheckEnabled) return;
        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (data.joinGraceTicks > 0) return;

        int currentHash = 0;
        int armorCount = 0;
        var armorSlots = player.getInventory().armor;
        for (int i = 0; i < armorSlots.size(); i++) {
            ItemStack stack = armorSlots.get(i);
            if (!stack.isEmpty()) {
                currentHash += stack.getItem().hashCode() + i * 31;
                armorCount++;
            }
        }

        if (data.prevArmorHash != 0 && currentHash != data.prevArmorHash) {
            long now = System.currentTimeMillis();

            if (armorCount < data.prevArmorCount) {
                data.prevArmorHash = currentHash;
                data.prevArmorCount = armorCount;
                return;
            }

            boolean isEquip = armorCount > data.prevArmorCount;
            if (!isEquip) {
                // Same count but different item, only count if recently damaged
                if ((now - data.lastDamageTime) > AFTER_DAMAGE_MS) {
                    data.prevArmorHash = currentHash;
                    data.prevArmorCount = armorCount;
                    return;
                }
                isEquip = true;
            }

            data.armorEquipTimes.addLast(now);
            data.lastArmorEquipTime = now;
            while (!data.armorEquipTimes.isEmpty() && now - data.armorEquipTimes.peekFirst() > WINDOW_LONG_MS) data.armorEquipTimes.pollFirst();

            int equips = data.armorEquipTimes.size();
            long oldest = data.armorEquipTimes.peekFirst() != null ? data.armorEquipTimes.peekFirst() : now;
            long span = now - oldest;
            boolean afterDamage = (now - data.lastDamageTime) <= AFTER_DAMAGE_MS;

            boolean suspicious = false;
            if (equips >= MAX_SHORT && span <= WINDOW_SHORT_MS) suspicious = true;
            else if (equips >= MAX_LONG && span <= WINDOW_LONG_MS) suspicious = true;
            else if (afterDamage && equips >= 2 && span <= 500L) suspicious = true;
            else if (afterDamage && equips >= 1 && span <= 200L) suspicious = true;

            if (suspicious && data.canFlag(getName(), 1500)) {
                ViolationManager.flag(player, data, this,
                        String.format("Armor hash %d->%d count %d->%d equips %d in %dms afterDmg %s",
                                data.prevArmorHash, currentHash, data.prevArmorCount, armorCount, equips, span, afterDamage));
                data.armorEquipTimes.clear();
            }
        }
        data.prevArmorHash = currentHash;
        data.prevArmorCount = armorCount;
    }
}

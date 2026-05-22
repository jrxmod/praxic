package com.jrxmod.praxic.checks;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public class SprintCheck extends AbstractCheck {

    // Minimum food level that allows sprinting in vanilla (exclusive).
    // Vanilla threshold: player cannot START sprinting at foodLevel <= 6.
    private static final int SPRINT_MIN_FOOD = 6;

    @Override
    public String getName() {
        return "SprintCheck";
    }

    @Override
    public void check(ServerPlayer player, PlayerData data) {
        if (!Praxic.getConfig().sprintCheckEnabled) return;

        if (player.isSpectator()) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;
        if (player.getAbilities().mayfly) return;
        if (!player.isSprinting()) return;

        // Sprint while starving — vanilla prevents this
        int foodLevel = player.getFoodData().getFoodLevel();
        if (foodLevel <= SPRINT_MIN_FOOD) {
            if (data.canFlag(getName(), 1500)) {
                ViolationManager.flag(player, data, this,
                        String.format("Sprinting with food level %d (min: %d)",
                                foodLevel, SPRINT_MIN_FOOD + 1));
            }
            return;
        }

        // Sprint while blind — vanilla cancels sprint on Blindness application
        if (player.hasEffect(MobEffects.BLINDNESS)) {
            if (data.canFlag(getName(), 1500)) {
                ViolationManager.flag(player, data, this,
                        "Sprinting while Blindness effect is active");
            }
        }
    }
}

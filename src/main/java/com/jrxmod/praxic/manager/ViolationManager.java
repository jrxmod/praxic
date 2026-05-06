package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ViolationManager {

    public static void flag(ServerPlayer player, PlayerData data, AbstractCheck check, String details) {
        data.addViolation(check.getName());
        int violations = data.getViolations(check.getName());

        if (Praxic.getConfig().enableLogging) {
            Praxic.LOGGER.warn("[PRAXIC] {} | Player: {} | VL: {} | {}",
                    check.getName(),
                    player.getName().getString(),
                    violations,
                    details);
        }

        String action = getAction(check.getName());
        int maxViolations = getMaxViolations(check.getName());

        if (violations < maxViolations) {
            return;
        }

        switch (action.toLowerCase()) {
            case "kick" -> {
                player.connection.disconnect(
                        Component.literal("§c[PRAXIC] You have been kicked.\n§7Reason: §f" + check.getName())
                );
                data.resetViolations(check.getName());
                Praxic.LOGGER.warn("[PRAXIC] Player {} was KICKED by {}.",
                        player.getName().getString(), check.getName());
            }
            case "warn" -> {
                player.sendSystemMessage(
                        Component.literal("§e[PRAXIC] Warning: suspicious behavior detected. (" + check.getName() + ")")
                );
                data.resetViolations(check.getName());
            }
            default -> Praxic.LOGGER.warn("[PRAXIC] Unknown action '{}' for check {}", action, check.getName());
        }
    }

    private static String getAction(String checkName) {
        return switch (checkName) {
            case "FlyCheck" -> Praxic.getConfig().flyAction;
            case "SpeedCheck" -> Praxic.getConfig().speedAction;
            default -> "warn";
        };
    }

    private static int getMaxViolations(String checkName) {
        return switch (checkName) {
            case "FlyCheck" -> Praxic.getConfig().flyMaxViolations;
            case "SpeedCheck" -> Praxic.getConfig().speedMaxViolations;
            default -> 10;
        };
    }
}

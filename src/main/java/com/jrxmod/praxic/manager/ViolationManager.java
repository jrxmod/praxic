package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

public class ViolationManager {

    public static void flag(ServerPlayer player, PlayerData data, AbstractCheck check, String details) {
        data.addViolation(check.getName());
        int violations = data.getViolations(check.getName());

        // Standard logging
        if (Praxic.getConfig().enableLogging) {
            Praxic.LOGGER.warn("[PRAXIC] {} | Player: {} | VL: {} | {}",
                    check.getName(),
                    player.getName().getString(),
                    violations,
                    details);
            PraxicLogger.logViolation(check.getName(), player.getName().getString(), violations, details);
        }

        // Staff Alerts
        if (Praxic.getConfig().enableStaffAlerts) {
            Component alert = Component.literal(
                "§6[PRAXIC] §bStaff Alert §8» §fPlayer §e" + player.getName().getString() +
                " §fflagged §b" + check.getName() + " §7(VL: §e" + violations + "§7)"
            );
            player.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(p -> p.sendSystemMessage(alert));
        }

        String action = getAction(check.getName());
        int maxViolations = getMaxViolations(check.getName());

        if (violations < maxViolations) {
            return;
        }

        switch (action.toLowerCase()) {
            case "ban" -> {
                UserBanListEntry ban = new UserBanListEntry(
                        player.getGameProfile(),
                        null,
                        "PRAXIC",
                        null,
                        check.getName()
                );
                player.getServer().getPlayerList().getBans().add(ban);
                player.connection.disconnect(
                    Component.literal(
                        "§6§lPRAXIC §8§m──────────────§r\n\n" +
                        "§cYou have been §l§cpermanently banned§r§c.\n\n" +
                        "§7Reason: §f" + check.getName() + "\n" +
                        "§7Violations: §c" + violations + "\n\n" +
                        "§8If you think this is a mistake,\n" +
                        "§8contact server administration."
                    )
                );
                data.resetViolations(check.getName());
                Praxic.LOGGER.warn("[PRAXIC] Player {} was BANNED by {}.",
                        player.getName().getString(), check.getName());
                PraxicLogger.logKick(player.getName().getString(), check.getName());
            }
            case "kick" -> {
                player.connection.disconnect(
                    Component.literal(
                        "§6§lPRAXIC §8§m──────────────§r\n\n" +
                        "§cYou have been §l§ckicked§r§c.\n\n" +
                        "§7Reason: §f" + check.getName() + "\n" +
                        "§7Violations: §c" + violations + "\n\n" +
                        "§8If you think this is a mistake,\n" +
                        "§8contact server administration."
                    )
                );
                data.resetViolations(check.getName());
                Praxic.LOGGER.warn("[PRAXIC] Player {} was KICKED by {}.",
                        player.getName().getString(), check.getName());
                PraxicLogger.logKick(player.getName().getString(), check.getName());
            }
            case "warn" -> {
                player.sendSystemMessage(
                    Component.literal(
                        "§6[PRAXIC] §eWarning! §7Suspicious behavior detected.\n" +
                        "§8» §7Check: §f" + check.getName() + " §8| §7VL: §e" + violations
                    )
                );
                data.resetViolations(check.getName());
            }
            default -> Praxic.LOGGER.warn("[PRAXIC] Unknown action '{}' for check {}", action, check.getName());
        }
    }

    private static String getAction(String checkName) {
        return switch (checkName) {
            case "FlyCheck"        -> Praxic.getConfig().flyAction;
            case "SpeedCheck"      -> Praxic.getConfig().speedAction;
            case "NoFallCheck"     -> Praxic.getConfig().noFallAction;
            case "ReachCheck"      -> Praxic.getConfig().reachAction;
            case "KillAuraCheck"   -> Praxic.getConfig().killAuraCheckAction;
            case "ScaffoldCheck"   -> Praxic.getConfig().scaffoldAction;
            case "AutoTotemCheck"  -> Praxic.getConfig().autoTotemAction;
            case "InventoryCheck"  -> Praxic.getConfig().inventoryAction;
            default -> "warn";
        };
    }

    private static int getMaxViolations(String checkName) {
        return switch (checkName) {
            case "FlyCheck"        -> Praxic.getConfig().flyMaxViolations;
            case "SpeedCheck"      -> Praxic.getConfig().speedMaxViolations;
            case "NoFallCheck"     -> Praxic.getConfig().noFallMaxViolations;
            case "ReachCheck"      -> Praxic.getConfig().reachMaxViolations;
            case "KillAuraCheck"   -> Praxic.getConfig().killAuraCheckMaxViolations;
            case "ScaffoldCheck"   -> Praxic.getConfig().scaffoldMaxViolations;
            case "AutoTotemCheck"  -> Praxic.getConfig().autoTotemMaxViolations;
            case "InventoryCheck"  -> Praxic.getConfig().inventoryMaxViolations;
            default -> 10;
        };
    }
}

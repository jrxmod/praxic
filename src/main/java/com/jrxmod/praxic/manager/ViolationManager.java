package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicViolationEvent;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.util.DiscordWebhook;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

import java.util.Set;

public class ViolationManager {

    public static void flag(ServerPlayer player, PlayerData data, AbstractCheck check, String details) {
        data.addViolation(check.getName());
        int violations = data.getViolations(check.getName());

        String action = getAction(check.getName());
        int maxViolations = getMaxViolations(check.getName());
        String resolvedAction = violations >= maxViolations ? action : "flag";

        // Record to persistent history
        Praxic.getHistoryManager().record(
                player.getUUID(),
                check.getName(),
                violations,
                details,
                resolvedAction
        );

        // Standard logging — always runs regardless of cancellation
        if (Praxic.getConfig().enableLogging) {
            Praxic.LOGGER.warn("[PRAXIC] {} | Player: {} | VL: {} | {}",
                    check.getName(),
                    player.getName().getString(),
                    violations,
                    details);
            PraxicLogger.logViolation(check.getName(), player.getName().getString(), violations, details);
        }

        // Staff Alerts — always runs regardless of cancellation
        if (Praxic.getConfig().enableStaffAlerts) {
            Component alert = Component.literal(
                "§6[PRAXIC] §bStaff Alert §8» §fPlayer §e" + player.getName().getString() +
                " §fflagged §b" + check.getName() + " §7(VL: §e" + violations + "§7)"
            );
            player.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(p -> p.sendSystemMessage(alert));
        }

        // Discord Webhook — always runs regardless of cancellation
        DiscordWebhook.send(
                player.getName().getString(),
                check.getName(),
                violations,
                details,
                resolvedAction
        );

        // Fire event — if any listener returns true, skip PRAXIC's built-in action
        // REVEX (or any other addon) returning true means it handles punishment itself
        boolean cancelled = PraxicViolationEvent.EVENT.invoker().onViolation(
                player,
                check.getName(),
                violations,
                details,
                resolvedAction
        );

        if (cancelled) {
            Praxic.LOGGER.info("[PRAXIC] Action for {} cancelled by event listener (addon handling punishment).",
                    player.getName().getString());
            return;
        }

        if (violations < maxViolations) {
            return;
        }

        // Human-readable reason shown to the player
        String reason = getHumanReason(check.getName());

        switch (action.toLowerCase()) {
            case "ban" -> {
                UserBanListEntry ban = new UserBanListEntry(
                        player.getGameProfile(),
                        null,
                        "PRAXIC",
                        null,
                        reason
                );
                player.getServer().getPlayerList().getBans().add(ban);
                player.connection.disconnect(
                    Component.literal(
                        "§6§lPRAXIC §8§m──────────────§r\n\n" +
                        "§cYou have been §l§cpermanently banned§r§c.\n\n" +
                        "§7Reason: §f" + reason + "\n\n" +
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
                        "§7Reason: §f" + reason + "\n\n" +
                        "§8If you think this is a mistake,\n" +
                        "§8contact server administration."
                    )
                );
                data.resetViolations(check.getName());
                Praxic.LOGGER.warn("[PRAXIC] Player {} was KICKED by {}.",
                        player.getName().getString(), check.getName());
                PraxicLogger.logKick(player.getName().getString(), check.getName());
            }
            case "setback" -> {
                player.connection.teleport(
                        data.lastSafeX,
                        data.lastSafeY,
                        data.lastSafeZ,
                        player.getYRot(),
                        player.getXRot(),
                        Set.of()
                );
                data.resetViolations(check.getName());
                Praxic.LOGGER.warn("[PRAXIC] Player {} was SET BACK by {}.",
                        player.getName().getString(), check.getName());
                PraxicLogger.logViolation(check.getName(), player.getName().getString(), violations,
                        "setback to " + String.format("%.1f %.1f %.1f",
                                data.lastSafeX, data.lastSafeY, data.lastSafeZ));
            }
            case "warn" -> {
                player.sendSystemMessage(
                    Component.literal(
                        "§6[PRAXIC] §eWarning! §7Suspicious activity detected.\n" +
                        "§8» §7" + reason
                    )
                );
                data.resetViolations(check.getName());
            }
            default -> Praxic.LOGGER.warn("[PRAXIC] Unknown action '{}' for check {}", action, check.getName());
        }
    }

    // -------------------------------------------------------------------------
    // Human-readable reason shown to the player on kick/ban/warn
    // -------------------------------------------------------------------------

    private static String getHumanReason(String checkName) {
        return switch (checkName) {
            case "FlyCheck"          -> "Flying is not allowed on this server.";
            case "YPredictionCheck"  -> "Flying is not allowed on this server.";
            case "SpeedCheck"        -> "Movement speed limit exceeded.";
            case "NoFallCheck"       -> "Fall damage manipulation is not allowed.";
            case "ReachCheck"        -> "Attack reach limit exceeded.";
            case "KillAuraCheck"     -> "Automated combat is not allowed.";
            case "ScaffoldCheck"     -> "Automated block placement is not allowed.";
            case "AutoTotemCheck"    -> "Automated item usage is not allowed.";
            case "InventoryCheck"    -> "Automated inventory manipulation is not allowed.";
            case "AutoClickerCheck"  -> "Automated clicking is not allowed.";
            case "TimerCheck"        -> "Game speed manipulation is not allowed.";
            case "FastBreakCheck"    -> "Block breaking speed limit exceeded.";
            case "JesusCheck"        -> "Walking on liquids is not allowed.";
            case "VelocityCheck"     -> "Knockback manipulation is not allowed.";
            case "RotationCheck"     -> "Suspicious aim behaviour detected.";
            case "SprintCheck"       -> "Illegal movement behaviour detected.";
            case "BoatFlyCheck"      -> "Flying is not allowed on this server.";
            default                  -> "Suspicious behaviour detected.";
        };
    }

    // -------------------------------------------------------------------------
    // Config lookups
    // -------------------------------------------------------------------------

    private static String getAction(String checkName) {
        return switch (checkName) {
            case "FlyCheck"          -> Praxic.getConfig().flyAction;
            case "SpeedCheck"        -> Praxic.getConfig().speedAction;
            case "NoFallCheck"       -> Praxic.getConfig().noFallAction;
            case "ReachCheck"        -> Praxic.getConfig().reachAction;
            case "KillAuraCheck"     -> Praxic.getConfig().killAuraCheckAction;
            case "ScaffoldCheck"     -> Praxic.getConfig().scaffoldAction;
            case "AutoTotemCheck"    -> Praxic.getConfig().autoTotemAction;
            case "InventoryCheck"    -> Praxic.getConfig().inventoryAction;
            case "AutoClickerCheck"  -> Praxic.getConfig().autoClickerAction;
            case "TimerCheck"        -> Praxic.getConfig().timerAction;
            case "FastBreakCheck"    -> Praxic.getConfig().fastBreakAction;
            case "JesusCheck"        -> Praxic.getConfig().jesusAction;
            case "VelocityCheck"     -> Praxic.getConfig().velocityAction;
            case "YPredictionCheck"  -> Praxic.getConfig().yPredictionAction;
            case "RotationCheck"     -> Praxic.getConfig().rotationAction;
            case "SprintCheck"       -> Praxic.getConfig().sprintAction;
            case "BoatFlyCheck"      -> Praxic.getConfig().boatFlyAction;
            default -> "warn";
        };
    }

    private static int getMaxViolations(String checkName) {
        return switch (checkName) {
            case "FlyCheck"          -> Praxic.getConfig().flyMaxViolations;
            case "SpeedCheck"        -> Praxic.getConfig().speedMaxViolations;
            case "NoFallCheck"       -> Praxic.getConfig().noFallMaxViolations;
            case "ReachCheck"        -> Praxic.getConfig().reachMaxViolations;
            case "KillAuraCheck"     -> Praxic.getConfig().killAuraCheckMaxViolations;
            case "ScaffoldCheck"     -> Praxic.getConfig().scaffoldMaxViolations;
            case "AutoTotemCheck"    -> Praxic.getConfig().autoTotemMaxViolations;
            case "InventoryCheck"    -> Praxic.getConfig().inventoryMaxViolations;
            case "AutoClickerCheck"  -> Praxic.getConfig().autoClickerMaxViolations;
            case "TimerCheck"        -> Praxic.getConfig().timerMaxViolations;
            case "FastBreakCheck"    -> Praxic.getConfig().fastBreakMaxViolations;
            case "JesusCheck"        -> Praxic.getConfig().jesusMaxViolations;
            case "VelocityCheck"     -> Praxic.getConfig().velocityMaxViolations;
            case "YPredictionCheck"  -> Praxic.getConfig().yPredictionMaxViolations;
            case "RotationCheck"     -> Praxic.getConfig().rotationMaxViolations;
            case "SprintCheck"       -> Praxic.getConfig().sprintMaxViolations;
            case "BoatFlyCheck"      -> Praxic.getConfig().boatFlyMaxViolations;
            default -> 10;
        };
    }
}

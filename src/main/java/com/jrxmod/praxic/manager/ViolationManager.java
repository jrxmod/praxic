package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicViolationEvent;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.decision.ActionResolver;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.util.DiscordWebhook;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Orchestration layer for violation handling.
 *
 * Responsibilities (always, regardless of cancellation):
 *   1. Increment legacy VL in PlayerData (used by REVEX event payload)
 *   2. Feed ConfidenceEngine — updates player's evidence score
 *   3. Persistent history record
 *   4. Server log + file log
 *   5. Staff alerts
 *   6. Discord webhook
 *   7. Fire PraxicViolationEvent (REVEX / addons)
 *
 * If event not cancelled:
 *   8. ActionResolver.execute() — punishment based on confidence score
 */
public class ViolationManager {

    public static void flag(ServerPlayer player, PlayerData data, AbstractCheck check, String details) {
        UUID   uuid      = player.getUUID();
        String checkName = check.getName();

        // 1. Increment legacy VL — still used as the int payload in PraxicViolationEvent
        data.addViolation(checkName);
        int violations = data.getViolations(checkName);

        // 2. Feed ConfidenceEngine
        Praxic.getConfidenceEngine().flag(uuid, checkName);
        double confidence     = Praxic.getConfidenceEngine().getScore(uuid);
        String resolvedAction = ActionResolver.resolve(confidence);

        // 3. Persistent history
        Praxic.getHistoryManager().record(uuid, checkName, violations, details, resolvedAction);

        // 4. Logging
        if (Praxic.getConfig().enableLogging) {
            Praxic.LOGGER.warn("[PRAXIC] {} | Player: {} | VL: {} | Confidence: {} | {}",
                    checkName,
                    player.getName().getString(),
                    violations,
                    String.format("%.3f", confidence),
                    details);
            PraxicLogger.logViolation(checkName, player.getName().getString(), violations, details);
        }

        // 5. Staff alerts
        if (Praxic.getConfig().enableStaffAlerts) {
            Component alert = Component.literal(
                    "§6[PRAXIC] §bStaff Alert §8» §fPlayer §e" + player.getName().getString() +
                    " §fflagged §b" + checkName +
                    " §7(VL: §e" + violations +
                    " §7| Conf: §e" + String.format("%.2f", confidence) + "§7)"
            );
            player.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(p -> p.sendSystemMessage(alert));
        }

        // 6. Discord webhook
        DiscordWebhook.send(player.getName().getString(), checkName, violations, details, resolvedAction);

        // 7. Fire event — REVEX or any addon can intercept the punishment
        boolean cancelled = PraxicViolationEvent.EVENT.invoker().onViolation(
                player, checkName, violations, details, resolvedAction);

        if (cancelled) {
            Praxic.LOGGER.info("[PRAXIC] Action for {} cancelled by event listener.",
                    player.getName().getString());
            return;
        }

        // 8. Execute action if confidence warrants it
        if (resolvedAction.equals("flag")) return;
        ActionResolver.execute(player, data, resolvedAction, checkName, getHumanReason(checkName), violations);
    }

    // -------------------------------------------------------------------------
    // Human-readable reason shown to the player
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
            case "PostKillSnapCheck" -> "Automated combat is not allowed.";
            default                  -> "Suspicious behaviour detected.";
        };
    }
}

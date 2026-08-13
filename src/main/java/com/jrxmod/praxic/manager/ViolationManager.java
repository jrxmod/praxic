package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicViolationEvent;
import com.jrxmod.praxic.checks.AbstractCheck;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.decision.ActionResolver;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.util.DiscordWebhook;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestration layer for violation handling.
 *
 * Responsibilities (always, regardless of cancellation):
 *   1. Increment legacy VL in PlayerData (used by REVEX event payload)
 *   2. Feed ConfidenceEngine — updates player's evidence score
 *   3. Persistent history and rich evidence record
 *   4. Server log + file log
 *   5. Staff alerts (rate-limited)
 *   6. Discord webhook (rate-limited)
 *   7. Fire PraxicViolationEvent (REVEX / addons)
 *
 * If event not cancelled:
 *   8. ActionResolver.execute() — punishment based on confidence score,
 *      capped by each check's configured maximum action.
 */
public class ViolationManager {

    private static final Map<String, Long> STAFF_ALERT_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, Long> DISCORD_ALERT_TIMES = new ConcurrentHashMap<>();

    public static void flag(ServerPlayer player, PlayerData data, AbstractCheck check, String details) {
        UUID   uuid      = player.getUUID();
        String checkName = check.getName();

        // 1. Increment legacy VL — still used as the int payload in PraxicViolationEvent
        data.addViolation(checkName);
        int violations = data.getViolations(checkName);

        // 2. Feed ConfidenceEngine
        Praxic.getConfidenceEngine().flag(uuid, checkName);
        double confidence = Praxic.getConfidenceEngine().getScore(uuid);
        double anomaly    = Praxic.getAnomalyScoreEngine().getScore(uuid);
        String resolvedAction = ActionResolver.resolve(confidence, getConfiguredAction(checkName));

        // 3. Persistent history + evidence
        Praxic.getHistoryManager().record(uuid, checkName, violations, details, resolvedAction);
        if (Praxic.getEvidenceManager() != null) {
            Praxic.getEvidenceManager().record(player, data, checkName, violations, details,
                    resolvedAction, confidence, anomaly);
        }

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

        // 5. Staff alerts — clickable player names with hover details
        if (Praxic.getConfig().enableStaffAlerts
                && shouldEmit(STAFF_ALERT_TIMES, uuid, checkName, Praxic.getConfig().staffAlertCooldownMs)) {
            String playerName = player.getName().getString();
            String confStr = String.format("%.2f", confidence);
            String pingStr = String.valueOf(player.connection.latency());

            MutableComponent alert = Component.literal("§6[PRAXIC] §bAlert §8» §f")
                    .append(Component.literal("§e" + playerName + "§f")
                            .withStyle(s -> s
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/praxic check " + playerName))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal(
                                                    "§e" + playerName + "\n" +
                                                    "§7Check: §b" + checkName + "\n" +
                                                    "§7VL: §e" + violations +
                                                    " §7| Conf: §e" + confStr + "\n" +
                                                    "§7Action: §e" + resolvedAction +
                                                    " §7| Ping: §e" + pingStr + "ms" + "\n" +
                                                    "§7" + details + "\n\n" +
                                                    "§8Click to inspect"
                                            )))))
                    .append(Component.literal(" §7→ §b" + checkName +
                            " §8(VL §e" + violations + "§8 | §e" + confStr + "§8)"));

            player.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.hasPermissions(2))
                    .forEach(p -> p.sendSystemMessage(alert));
        }

        // 6. Discord webhook
        if (shouldEmit(DISCORD_ALERT_TIMES, uuid, checkName, Praxic.getConfig().discordAlertCooldownMs)) {
            DiscordWebhook.send(player.getName().getString(), checkName, violations, details, resolvedAction);
        }

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

    public static void cleanup(UUID uuid) {
        String prefix = uuid.toString() + ":";
        STAFF_ALERT_TIMES.keySet().removeIf(k -> k.startsWith(prefix));
        DISCORD_ALERT_TIMES.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static boolean shouldEmit(Map<String, Long> map, UUID uuid, String checkName, long cooldownMs) {
        if (cooldownMs <= 0) return true;
        String key = uuid + ":" + checkName;
        long now = System.currentTimeMillis();
        long last = map.getOrDefault(key, 0L);
        if (now - last < cooldownMs) return false;
        map.put(key, now);
        return true;
    }

    // -------------------------------------------------------------------------
    // Configured maximum action per check
    // -------------------------------------------------------------------------

    private static String getConfiguredAction(String checkName) {
        return switch (checkName) {
            case "FlyCheck"          -> Praxic.getConfig().flyAction;
            case "YPredictionCheck"  -> Praxic.getConfig().yPredictionAction;
            case "SpeedCheck"        -> Praxic.getConfig().speedAction;
            case "PhaseCheck"        -> Praxic.getConfig().phaseAction;
            case "NoSlowCheck"       -> Praxic.getConfig().noSlowAction;
            case "NoFallCheck"       -> Praxic.getConfig().noFallAction;
            case "ReachCheck"        -> Praxic.getConfig().reachAction;
            case "KillAuraCheck"     -> Praxic.getConfig().killAuraCheckAction;
            case "GhostTrapCheck"    -> Praxic.getConfig().ghostTrapAction;
            case "CriticalsCheck"    -> Praxic.getConfig().criticalsAction;
            case "ScaffoldCheck"     -> Praxic.getConfig().scaffoldAction;
            case "AutoTotemCheck"    -> Praxic.getConfig().autoTotemAction;
            case "InventoryCheck"    -> Praxic.getConfig().inventoryAction;
            case "AutoClickerCheck"  -> Praxic.getConfig().autoClickerAction;
            case "TimerCheck"        -> Praxic.getConfig().timerAction;
            case "BadPacketsCheck"   -> Praxic.getConfig().badPacketsAction;
            case "FastBreakCheck"    -> Praxic.getConfig().fastBreakAction;
            case "JesusCheck"        -> Praxic.getConfig().jesusAction;
            case "VelocityCheck"     -> Praxic.getConfig().velocityAction;
            case "RotationCheck"     -> Praxic.getConfig().rotationAction;
            case "SprintCheck"       -> Praxic.getConfig().sprintAction;
            case "BoatFlyCheck"      -> Praxic.getConfig().boatFlyAction;
            case "PostKillSnapCheck" -> Praxic.getConfig().postKillSnapAction;
            case "ElytraFlyCheck"    -> Praxic.getConfig().elytraFlyAction;
            case "StepCheck"         -> Praxic.getConfig().stepAction;
            case "TowerCheck"        -> Praxic.getConfig().towerAction;
            case "GroundSpoofCheck"  -> Praxic.getConfig().groundSpoofAction;
            case "FastPlaceCheck"    -> Praxic.getConfig().fastPlaceAction;
            case "TeleportCheck"     -> Praxic.getConfig().teleportAction;
            default                  -> "kick";
        };
    }

    // -------------------------------------------------------------------------
    // Human-readable reason shown to the player
    // -------------------------------------------------------------------------

    private static String getHumanReason(String checkName) {
        return switch (checkName) {
            case "FlyCheck"          -> "Flying is not allowed on this server.";
            case "YPredictionCheck"  -> "Flying is not allowed on this server.";
            case "SpeedCheck"        -> "Movement speed limit exceeded.";
            case "PhaseCheck"        -> "No-clip movement is not allowed.";
            case "NoSlowCheck"       -> "Movement slowdown bypass is not allowed.";
            case "NoFallCheck"       -> "Fall damage manipulation is not allowed.";
            case "ReachCheck"        -> "Attack reach limit exceeded.";
            case "KillAuraCheck"     -> "Automated combat is not allowed.";
            case "GhostTrapCheck"    -> "Automated combat is not allowed.";
            case "CriticalsCheck"    -> "Critical-hit spoofing is not allowed.";
            case "ScaffoldCheck"     -> "Automated block placement is not allowed.";
            case "AutoTotemCheck"    -> "Automated item usage is not allowed.";
            case "InventoryCheck"    -> "Automated inventory manipulation is not allowed.";
            case "AutoClickerCheck"  -> "Automated clicking is not allowed.";
            case "TimerCheck"        -> "Game speed manipulation is not allowed.";
            case "BadPacketsCheck"   -> "Invalid client packets are not allowed.";
            case "FastBreakCheck"    -> "Block breaking speed limit exceeded.";
            case "JesusCheck"        -> "Walking on liquids is not allowed.";
            case "VelocityCheck"     -> "Knockback manipulation is not allowed.";
            case "RotationCheck"     -> "Suspicious aim behaviour detected.";
            case "SprintCheck"       -> "Illegal movement behaviour detected.";
            case "BoatFlyCheck"      -> "Flying is not allowed on this server.";
            case "PostKillSnapCheck" -> "Automated combat is not allowed.";
            case "ElytraFlyCheck"    -> "Elytra flight manipulation is not allowed.";
            case "StepCheck"         -> "Illegal step height is not allowed.";
            case "TowerCheck"        -> "Automated tower building is not allowed.";
            case "GroundSpoofCheck"  -> "Ground state spoofing is not allowed.";
            case "FastPlaceCheck"    -> "Block placement rate limit exceeded.";
            case "TeleportCheck"     -> "Unexplained teleport is not allowed.";
            default                  -> "Suspicious behaviour detected.";
        };
    }
}

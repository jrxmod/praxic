package com.jrxmod.praxic.engine.decision;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

import java.util.Set;

/**
 * Translates a player's confidence score into a concrete action and executes it.
 * Replaces the per-check action switch that was in ViolationManager.
 *
 * Thresholds:
 *   < 0.30  → nothing  (not enough evidence yet)
 *   0.30–0.60 → warn
 *   0.60–0.80 → setback
 *   0.80–0.95 → kick
 *   ≥ 0.95    → ban
 *
 * ViolationManager remains the orchestration layer (logging, alerts, discord, event).
 * ActionResolver only handles the punishment execution.
 */
public class ActionResolver {

    // -------------------------------------------------------------------------
    // Confidence thresholds
    // -------------------------------------------------------------------------

    private static final double THRESHOLD_WARN    = 0.30;
    private static final double THRESHOLD_SETBACK = 0.60;
    private static final double THRESHOLD_KICK    = 0.80;
    private static final double THRESHOLD_BAN     = 0.95;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolves a confidence score to an action name.
     * "flag" means no punishment is taken this cycle.
     *
     * @param confidence current score from ConfidenceEngine (0.0–1.0)
     * @return one of: "flag", "warn", "setback", "kick", "ban"
     */
    public static String resolve(double confidence) {
        if (confidence >= THRESHOLD_BAN)    return "ban";
        if (confidence >= THRESHOLD_KICK)   return "kick";
        if (confidence >= THRESHOLD_SETBACK) return "setback";
        if (confidence >= THRESHOLD_WARN)   return "warn";
        return "flag";
    }

    /**
     * Executes a resolved action against a player.
     * Called by ViolationManager after the REVEX event fires and is not cancelled.
     *
     * @param player    target player
     * @param data      player state for setback position and VL reset
     * @param action    resolved action string from resolve()
     * @param checkName check that triggered the flag (for logging)
     * @param reason    human-readable reason shown to the player
     * @param violations current legacy VL for the triggering check
     */
    public static void execute(
            ServerPlayer player,
            PlayerData   data,
            String       action,
            String       checkName,
            String       reason,
            int          violations
    ) {
        switch (action.toLowerCase()) {
            case "ban" -> {
                UserBanListEntry ban = new UserBanListEntry(
                        player.getGameProfile(),
                        null, "PRAXIC", null, reason
                );
                player.getServer().getPlayerList().getBans().add(ban);
                player.connection.disconnect(Component.literal(
                        "§6§lPRAXIC §8§m──────────────§r\n\n" +
                        "§cYou have been §l§cpermanently banned§r§c.\n\n" +
                        "§7Reason: §f" + reason + "\n\n" +
                        "§8If you think this is a mistake,\n" +
                        "§8contact server administration."
                ));
                data.resetViolations(checkName);
                Praxic.LOGGER.warn("[PRAXIC] Player {} was BANNED by {}.",
                        player.getName().getString(), checkName);
                PraxicLogger.logKick(player.getName().getString(), checkName);
            }
            case "kick" -> {
                player.connection.disconnect(Component.literal(
                        "§6§lPRAXIC §8§m──────────────§r\n\n" +
                        "§cYou have been §l§ckicked§r§c.\n\n" +
                        "§7Reason: §f" + reason + "\n\n" +
                        "§8If you think this is a mistake,\n" +
                        "§8contact server administration."
                ));
                data.resetViolations(checkName);
                Praxic.LOGGER.warn("[PRAXIC] Player {} was KICKED by {}.",
                        player.getName().getString(), checkName);
                PraxicLogger.logKick(player.getName().getString(), checkName);
            }
            case "setback" -> {
                player.connection.teleport(
                        data.lastSafeX, data.lastSafeY, data.lastSafeZ,
                        player.getYRot(), player.getXRot(), Set.of()
                );
                data.resetViolations(checkName);
                Praxic.LOGGER.warn("[PRAXIC] Player {} was SET BACK by {}.",
                        player.getName().getString(), checkName);
                PraxicLogger.logViolation(checkName, player.getName().getString(), violations,
                        "setback to " + String.format("%.1f %.1f %.1f",
                                data.lastSafeX, data.lastSafeY, data.lastSafeZ));
            }
            case "warn" -> {
                player.sendSystemMessage(Component.literal(
                        "§6[PRAXIC] §eWarning! §7Suspicious activity detected.\n" +
                        "§8» §7" + reason
                ));
                data.resetViolations(checkName);
            }
            // "flag" and unknown → no action
            default -> {}
        }
    }

    // Private constructor — static utility class
    private ActionResolver() {}
}

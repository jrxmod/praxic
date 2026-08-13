package com.jrxmod.praxic.engine.decision;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;

import java.util.Set;

/**
 * Translates a player's confidence score into a concrete action and executes it.
 *
 * v0.11.0 keeps the confidence gate from Watchtower, but restores per-check
 * configuration as a maximum-action cap. Example: if confidence resolves to
 * kick but SpeedCheck is configured as "warn", the final action is "warn".
 */
public class ActionResolver {

    // Fallback thresholds used before config exists.
    private static final double DEFAULT_THRESHOLD_WARN    = 0.30;
    private static final double DEFAULT_THRESHOLD_SETBACK = 0.60;
    private static final double DEFAULT_THRESHOLD_KICK    = 0.80;
    private static final double DEFAULT_THRESHOLD_BAN     = 0.95;

    /**
     * Resolves a confidence score to an action name.
     * "flag" means no punishment is taken this cycle.
     */
    public static String resolve(double confidence) {
        return resolve(confidence, "ban");
    }

    /**
     * Resolves a confidence score and caps the result by the check's configured
     * maximum action.
     *
     * @param confidence current score from ConfidenceEngine (0.0–1.0)
     * @param maxAction  configured cap: flag, warn, setback, kick or ban
     */
    public static String resolve(double confidence, String maxAction) {
        String base = resolveBase(confidence);
        if ("flag".equals(base)) return base;

        String cap = normalize(maxAction);
        if (cap == null) return base;
        if ("flag".equals(cap)) return "flag";

        return rank(base) > rank(cap) ? cap : base;
    }

    private static String resolveBase(double confidence) {
        PraxicConfig cfg = Praxic.getConfig();
        double warn    = cfg != null ? cfg.confidenceWarnThreshold    : DEFAULT_THRESHOLD_WARN;
        double setback = cfg != null ? cfg.confidenceSetbackThreshold : DEFAULT_THRESHOLD_SETBACK;
        double kick    = cfg != null ? cfg.confidenceKickThreshold    : DEFAULT_THRESHOLD_KICK;
        double ban     = cfg != null ? cfg.confidenceBanThreshold     : DEFAULT_THRESHOLD_BAN;
        boolean autoBan = cfg == null || cfg.confidenceAutoBan;

        if (confidence >= ban)     return autoBan ? "ban" : "kick";
        if (confidence >= kick)    return "kick";
        if (confidence >= setback) return "setback";
        if (confidence >= warn)    return "warn";
        return "flag";
    }

    private static String normalize(String action) {
        if (action == null) return null;
        return switch (action.toLowerCase()) {
            case "flag", "warn", "freeze", "setback", "kick", "ban" -> action.toLowerCase();
            default -> null;
        };
    }

    private static int rank(String action) {
        return switch (action) {
            case "flag"    -> 0;
            case "warn"    -> 1;
            case "freeze"  -> 2;
            case "setback" -> 3;
            case "kick"    -> 4;
            case "ban"     -> 5;
            default         -> 4;
        };
    }

    /**
     * Executes a resolved action against a player.
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
            case "freeze" -> {
                data.freezeTicksRemaining = Praxic.getConfig().freezeDurationTicks;
                data.freezeX = player.getX();
                data.freezeY = player.getY();
                data.freezeZ = player.getZ();
                data.freezeYaw = player.getYRot();
                data.freezePitch = player.getXRot();
                player.sendSystemMessage(Component.literal(
                        "§6[PRAXIC] §eYou have been §l§efrozen§r§e temporarily. §7" + reason
                ));
                data.resetViolations(checkName);
                Praxic.LOGGER.warn("[PRAXIC] Player {} was FROZEN by {}.",
                        player.getName().getString(), checkName);
                PraxicLogger.logViolation(checkName, player.getName().getString(), violations,
                        "frozen for " + Praxic.getConfig().freezeDurationTicks + " ticks");
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

    private ActionResolver() {}
}

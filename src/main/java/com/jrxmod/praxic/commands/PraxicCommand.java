package com.jrxmod.praxic.commands;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicStats;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.manager.HistoryManager;
import com.jrxmod.praxic.manager.WhitelistManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PraxicCommand {

    private static final String LINE   = "§8§m                                        §r";
    private static final String HEADER = "§8§m──§r §6§lPRAXIC §8Anticheat§r §8§m──§r";
    private static final String BULLET = " §8› §r";
    private static final String ON     = "§a✔ ON§r";
    private static final String OFF    = "§c✘ OFF§r";

    // Format one status row: name (gold) + separator + state
    private static String row(String label, boolean enabled) {
        return BULLET + "§e" + label + " §8— " + (enabled ? ON : OFF);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(Commands.literal("praxic")
                    .requires(source -> source.hasPermission(2))

                    // /praxic status
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                PraxicConfig cfg = Praxic.getConfig();

                                Praxic.LOGGER.info("[PRAXIC] Status: Fly={} Speed={} NoFall={} Reach={} " +
                                        "KillAura={} Scaffold={} AutoTotem={} Inventory={} AutoClicker={} " +
                                        "Timer={} FastBreak={} Jesus={} Velocity={} YPrediction={} Logging={}",
                                        cfg.flyCheckEnabled          ? "ON" : "OFF",
                                        cfg.speedCheckEnabled        ? "ON" : "OFF",
                                        cfg.noFallCheckEnabled       ? "ON" : "OFF",
                                        cfg.reachCheckEnabled        ? "ON" : "OFF",
                                        cfg.killAuraCheckEnabled     ? "ON" : "OFF",
                                        cfg.scaffoldCheckEnabled     ? "ON" : "OFF",
                                        cfg.autoTotemCheckEnabled    ? "ON" : "OFF",
                                        cfg.inventoryCheckEnabled    ? "ON" : "OFF",
                                        cfg.autoClickerCheckEnabled  ? "ON" : "OFF",
                                        cfg.timerCheckEnabled        ? "ON" : "OFF",
                                        cfg.fastBreakCheckEnabled    ? "ON" : "OFF",
                                        cfg.jesusCheckEnabled        ? "ON" : "OFF",
                                        cfg.velocityCheckEnabled     ? "ON" : "OFF",
                                        cfg.yPredictionCheckEnabled  ? "ON" : "OFF",
                                        cfg.enableLogging            ? "ON" : "OFF");

                                source.sendSuccess(() -> Component.literal(HEADER), false);

                                // Movement
                                source.sendSuccess(() -> Component.literal(" §8§oMovement"), false);
                                source.sendSuccess(() -> Component.literal(row("FlyCheck",         cfg.flyCheckEnabled)),         false);
                                source.sendSuccess(() -> Component.literal(row("YPredictionCheck", cfg.yPredictionCheckEnabled)), false);
                                source.sendSuccess(() -> Component.literal(row("SpeedCheck",       cfg.speedCheckEnabled)),       false);
                                source.sendSuccess(() -> Component.literal(row("JesusCheck",       cfg.jesusCheckEnabled)),       false);

                                // Combat
                                source.sendSuccess(() -> Component.literal(" §8§oCombat"), false);
                                source.sendSuccess(() -> Component.literal(row("ReachCheck",       cfg.reachCheckEnabled)),       false);
                                source.sendSuccess(() -> Component.literal(row("KillAuraCheck",    cfg.killAuraCheckEnabled)),    false);
                                source.sendSuccess(() -> Component.literal(row("VelocityCheck",    cfg.velocityCheckEnabled)),    false);

                                // World
                                source.sendSuccess(() -> Component.literal(" §8§oWorld"), false);
                                source.sendSuccess(() -> Component.literal(row("ScaffoldCheck",    cfg.scaffoldCheckEnabled)),    false);
                                source.sendSuccess(() -> Component.literal(row("FastBreakCheck",   cfg.fastBreakCheckEnabled)),   false);
                                source.sendSuccess(() -> Component.literal(row("NoFallCheck",      cfg.noFallCheckEnabled)),      false);

                                // Client
                                source.sendSuccess(() -> Component.literal(" §8§oClient"), false);
                                source.sendSuccess(() -> Component.literal(row("AutoClickerCheck", cfg.autoClickerCheckEnabled)), false);
                                source.sendSuccess(() -> Component.literal(row("AutoTotemCheck",   cfg.autoTotemCheckEnabled)),   false);
                                source.sendSuccess(() -> Component.literal(row("InventoryCheck",   cfg.inventoryCheckEnabled)),   false);
                                source.sendSuccess(() -> Component.literal(row("TimerCheck",       cfg.timerCheckEnabled)),       false);

                                // System
                                source.sendSuccess(() -> Component.literal(" §8§oSystem"), false);
                                source.sendSuccess(() -> Component.literal(row("Logging",          cfg.enableLogging)),           false);
                                source.sendSuccess(() -> Component.literal(row("StaffAlerts",      cfg.enableStaffAlerts)),       false);
                                source.sendSuccess(() -> Component.literal(row("Discord",          cfg.enableDiscordWebhook)),    false);

                                source.sendSuccess(() -> Component.literal(LINE), false);
                                return 1;
                            }))

                    // /praxic stats
                    .then(Commands.literal("stats")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                Map<UUID, PlayerData> allData = Praxic.getCheckManager().getAllData();

                                int totalFlags = PraxicStats.getTotalFlags();
                                Map<String, Integer> topChecks = PraxicStats.getTopChecks(3);

                                List<Map.Entry<String, Integer>> topPlayers = allData.entrySet().stream()
                                        .filter(e -> !e.getValue().violations.isEmpty())
                                        .map(e -> {
                                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(e.getKey());
                                            String name = p != null ? p.getName().getString() : e.getKey().toString();
                                            int total = e.getValue().violations.values().stream()
                                                    .mapToInt(Integer::intValue).sum();
                                            return Map.entry(name, total);
                                        })
                                        .sorted(Comparator.<Map.Entry<String, Integer>, Integer>
                                                comparing(Map.Entry::getValue).reversed())
                                        .limit(3)
                                        .toList();

                                source.sendSuccess(() -> Component.literal(HEADER), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7Session statistics:"), false);
                                source.sendSuccess(() -> Component.literal(LINE), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7Total flags §8— §e" + totalFlags), false);

                                source.sendSuccess(() -> Component.literal(BULLET + "§7Top checks:"), false);
                                if (topChecks.isEmpty()) {
                                    source.sendSuccess(() -> Component.literal("   §8No data yet."), false);
                                } else {
                                    topChecks.forEach((check, count) ->
                                        source.sendSuccess(() -> Component.literal(
                                                "   §8— §b" + check + " §8(" + count + "§8)"), false));
                                }

                                source.sendSuccess(() -> Component.literal(BULLET + "§7Top players:"), false);
                                if (topPlayers.isEmpty()) {
                                    source.sendSuccess(() -> Component.literal("   §8No data yet."), false);
                                } else {
                                    topPlayers.forEach(entry ->
                                        source.sendSuccess(() -> Component.literal(
                                                "   §8— §e" + entry.getKey() + " §8(" + entry.getValue() + " VL§8)"), false));
                                }

                                source.sendSuccess(() -> Component.literal(LINE), false);
                                return 1;
                            }))

                    // /praxic check <player>
                    .then(Commands.literal("check")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "player");
                                        CommandSourceStack source = ctx.getSource();
                                        ServerPlayer target = source.getServer()
                                                .getPlayerList().getPlayerByName(name);

                                        if (target == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
                                            return 0;
                                        }

                                        PlayerData data = Praxic.getCheckManager().getPlayerData(target.getUUID());
                                        if (data == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] No data for: §e" + name));
                                            return 0;
                                        }

                                        Praxic.LOGGER.info("[PRAXIC] Violations for {}: {}", name, data.violations);

                                        source.sendSuccess(() -> Component.literal(HEADER), false);
                                        source.sendSuccess(() -> Component.literal(BULLET + "§7Violations for §e" + name + "§7:"), false);
                                        source.sendSuccess(() -> Component.literal(LINE), false);

                                        if (data.violations.isEmpty()) {
                                            source.sendSuccess(() -> Component.literal(BULLET + "§aNo violations recorded."), false);
                                        } else {
                                            data.violations.forEach((check, count) -> {
                                                String color = count >= 5 ? "§c" : count >= 3 ? "§e" : "§a";
                                                source.sendSuccess(() -> Component.literal(
                                                        BULLET + "§f" + check + " §8— " + color + count + " VL"), false);
                                            });
                                        }
                                        source.sendSuccess(() -> Component.literal(LINE), false);
                                        return 1;
                                    })))

                    // /praxic violations
                    .then(Commands.literal("violations")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                Map<UUID, PlayerData> allData = Praxic.getCheckManager().getAllData();

                                source.sendSuccess(() -> Component.literal(HEADER), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7All player violations:"), false);
                                source.sendSuccess(() -> Component.literal(LINE), false);

                                boolean[] any = {false};
                                allData.forEach((uuid, pData) -> {
                                    if (!pData.violations.isEmpty()) {
                                        ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                                        String playerName = p != null ? p.getName().getString() : uuid.toString();
                                        Praxic.LOGGER.info("[PRAXIC] {} -> {}", playerName, pData.violations);
                                        source.sendSuccess(() -> Component.literal(
                                                BULLET + "§e" + playerName + " §8— §7" + pData.violations), false);
                                        any[0] = true;
                                    }
                                });

                                if (!any[0]) {
                                    source.sendSuccess(() -> Component.literal(BULLET + "§aNo violations recorded."), false);
                                }
                                source.sendSuccess(() -> Component.literal(LINE), false);
                                return 1;
                            }))

                    // /praxic reset <player>
                    .then(Commands.literal("reset")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "player");
                                        CommandSourceStack source = ctx.getSource();
                                        ServerPlayer target = source.getServer()
                                                .getPlayerList().getPlayerByName(name);

                                        if (target == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
                                            return 0;
                                        }

                                        PlayerData data = Praxic.getCheckManager().getPlayerData(target.getUUID());
                                        if (data == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] No data for: §e" + name));
                                            return 0;
                                        }

                                        data.violations.clear();
                                        data.lastFlagTime.clear();

                                        source.sendSuccess(() -> Component.literal(
                                                "§6[PRAXIC] §fViolations for §e" + name + " §fcleared."), false);
                                        Praxic.LOGGER.info("[PRAXIC] Violations reset for {} by {}", name, source.getTextName());
                                        PraxicLogger.logInfo("Violations reset for " + name + " by " + source.getTextName());
                                        return 1;
                                    })))

                    // /praxic reload
                    .then(Commands.literal("reload")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                Praxic.reloadConfig();
                                source.sendSuccess(() -> Component.literal(
                                        "§6[PRAXIC] §fConfig §areloaded§f."), false);
                                Praxic.LOGGER.info("[PRAXIC] Config reloaded by {}", source.getTextName());
                                return 1;
                            }))

                    // /praxic whitelist <add|remove|list> [player]
                    .then(Commands.literal("whitelist")
                            .then(Commands.literal("add")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "player");
                                                CommandSourceStack source = ctx.getSource();
                                                ServerPlayer target = source.getServer()
                                                        .getPlayerList().getPlayerByName(name);

                                                if (target == null) {
                                                    source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
                                                    return 0;
                                                }

                                                WhitelistManager wl = Praxic.getWhitelistManager();
                                                boolean added = wl.add(target.getUUID());

                                                if (added) {
                                                    source.sendSuccess(() -> Component.literal(
                                                            "§6[PRAXIC] §e" + name + " §fadded to whitelist."), false);
                                                    Praxic.LOGGER.info("[PRAXIC] {} added to whitelist by {}.", name, source.getTextName());
                                                    PraxicLogger.logInfo(name + " added to whitelist by " + source.getTextName());
                                                } else {
                                                    source.sendFailure(Component.literal("§c[PRAXIC] §e" + name + " §fis already whitelisted."));
                                                }
                                                return 1;
                                            })))
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "player");
                                                CommandSourceStack source = ctx.getSource();
                                                ServerPlayer target = source.getServer()
                                                        .getPlayerList().getPlayerByName(name);

                                                if (target == null) {
                                                    source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
                                                    return 0;
                                                }

                                                WhitelistManager wl = Praxic.getWhitelistManager();
                                                boolean removed = wl.remove(target.getUUID());

                                                if (removed) {
                                                    source.sendSuccess(() -> Component.literal(
                                                            "§6[PRAXIC] §e" + name + " §fremoved from whitelist."), false);
                                                    Praxic.LOGGER.info("[PRAXIC] {} removed from whitelist by {}.", name, source.getTextName());
                                                    PraxicLogger.logInfo(name + " removed from whitelist by " + source.getTextName());
                                                } else {
                                                    source.sendFailure(Component.literal("§c[PRAXIC] §e" + name + " §fis not whitelisted."));
                                                }
                                                return 1;
                                            })))
                            .then(Commands.literal("list")
                                    .executes(ctx -> {
                                        CommandSourceStack source = ctx.getSource();
                                        WhitelistManager wl = Praxic.getWhitelistManager();
                                        Set<UUID> all = wl.getAll();

                                        source.sendSuccess(() -> Component.literal(HEADER), false);
                                        source.sendSuccess(() -> Component.literal(BULLET + "§7Whitelist §8(" + all.size() + " entries)§7:"), false);
                                        source.sendSuccess(() -> Component.literal(LINE), false);

                                        if (all.isEmpty()) {
                                            source.sendSuccess(() -> Component.literal(BULLET + "§aNo players whitelisted."), false);
                                        } else {
                                            all.forEach(uuid -> {
                                                ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                                                String displayName = p != null ? p.getName().getString() : uuid.toString();
                                                String dot = p != null ? "§a● " : "§8○ ";
                                                source.sendSuccess(() -> Component.literal(
                                                        BULLET + dot + "§f" + displayName), false);
                                            });
                                        }
                                        source.sendSuccess(() -> Component.literal(LINE), false);
                                        return 1;
                                    })))

                    // /praxic history <player>
                    .then(Commands.literal("history")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "player");
                                        CommandSourceStack source = ctx.getSource();

                                        ServerPlayer target = source.getServer()
                                                .getPlayerList().getPlayerByName(name);

                                        UUID uuid = null;
                                        if (target != null) {
                                            uuid = target.getUUID();
                                        } else {
                                            com.mojang.authlib.GameProfile profile = source.getServer()
                                                    .getProfileCache() != null
                                                    ? source.getServer().getProfileCache()
                                                            .get(name).map(p -> p).orElse(null)
                                                    : null;
                                            if (profile != null) uuid = profile.getId();
                                        }

                                        if (uuid == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
                                            return 0;
                                        }

                                        final UUID finalUuid = uuid;
                                        List<HistoryManager.ViolationEntry> entries =
                                                Praxic.getHistoryManager().getHistory(finalUuid);

                                        source.sendSuccess(() -> Component.literal(HEADER), false);
                                        source.sendSuccess(() -> Component.literal(
                                                BULLET + "§7History for §e" + name +
                                                " §8(" + entries.size() + " entries)§7:"), false);
                                        source.sendSuccess(() -> Component.literal(LINE), false);

                                        if (entries.isEmpty()) {
                                            source.sendSuccess(() -> Component.literal(BULLET + "§aNo history recorded."), false);
                                        } else {
                                            int start = Math.max(0, entries.size() - 10);
                                            List<HistoryManager.ViolationEntry> recent =
                                                    entries.subList(start, entries.size());
                                            for (int i = recent.size() - 1; i >= 0; i--) {
                                                HistoryManager.ViolationEntry e = recent.get(i);
                                                String actionColor = switch (e.action) {
                                                    case "ban"     -> "§c";
                                                    case "kick"    -> "§e";
                                                    case "setback" -> "§b";
                                                    case "warn"    -> "§6";
                                                    default        -> "§7";
                                                };
                                                source.sendSuccess(() -> Component.literal(
                                                        BULLET + "§8[§7" + e.timestamp + "§8] " +
                                                        "§b" + e.check + " §8VL:" + e.vl +
                                                        " " + actionColor + e.action +
                                                        " §8— §7" + e.details), false);
                                            }
                                        }
                                        source.sendSuccess(() -> Component.literal(LINE), false);
                                        return 1;
                                    })))
            );
        });
    }
}

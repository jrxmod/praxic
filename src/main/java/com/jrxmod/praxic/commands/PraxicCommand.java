package com.jrxmod.praxic.commands;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.api.PraxicStats;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.CheckManager;
import com.jrxmod.praxic.engine.analysis.PlayerAnalytics;
import com.jrxmod.praxic.engine.analysis.PlayerBaseline;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.manager.DebugRecorder;
import com.jrxmod.praxic.manager.EvidenceManager;
import com.jrxmod.praxic.manager.HistoryManager;
import com.jrxmod.praxic.manager.WhitelistManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("praxic")
                    .requires(source -> source.hasPermission(2));

            root.then(Commands.literal("status").executes(PraxicCommand::cmdStatus));
            root.then(Commands.literal("dashboard").executes(PraxicCommand::cmdDashboard));
            root.then(Commands.literal("stats").executes(PraxicCommand::cmdStats));
            root.then(Commands.literal("perf").executes(PraxicCommand::cmdPerf));
            root.then(Commands.literal("check")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdCheck)));
            root.then(Commands.literal("violations").executes(PraxicCommand::cmdViolations));
            root.then(Commands.literal("reset")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdReset)));
            root.then(Commands.literal("reload").executes(PraxicCommand::cmdReload));
            root.then(Commands.literal("whitelist")
                    .then(Commands.literal("add")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                            if (!Praxic.getWhitelistManager().isWhitelisted(p.getUUID())) {
                                                builder.suggest(p.getName().getString());
                                            }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(PraxicCommand::cmdWhitelistAdd)))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        for (var id : Praxic.getWhitelistManager().getAll()) {
                                            var pl = ctx.getSource().getServer().getPlayerList().getPlayer(id);
                                            if (pl != null) builder.suggest(pl.getName().getString());
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(PraxicCommand::cmdWhitelistRemove)))
                    .then(Commands.literal("list").executes(PraxicCommand::cmdWhitelistList)));
            root.then(Commands.literal("history")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdHistory)));
            root.then(Commands.literal("debug")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdDebug)));
            root.then(Commands.literal("tp")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdTpFlag)));
            root.then(Commands.literal("evidence")
                    .executes(PraxicCommand::cmdEvidenceGlobal)
                    .then(Commands.literal("clear")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                            builder.suggest(p.getName().getString());
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(PraxicCommand::cmdEvidenceClear)))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                    builder.suggest(p.getName().getString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(PraxicCommand::cmdEvidencePlayer)));

            dispatcher.register(root);
        });
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PraxicConfig cfg = Praxic.getConfig();

        send(source, HEADER);

        send(source, " §8§oMovement");
        send(source, row("FlyCheck",          cfg.flyCheckEnabled));
        send(source, row("YPredictionCheck",  cfg.yPredictionCheckEnabled));
        send(source, row("SpeedCheck",        cfg.speedCheckEnabled));
        send(source, row("PhaseCheck",        cfg.phaseCheckEnabled));
        send(source, row("NoSlowCheck",       cfg.noSlowCheckEnabled));
        send(source, row("JesusCheck",        cfg.jesusCheckEnabled));
        send(source, row("SprintCheck",       cfg.sprintCheckEnabled));
        send(source, row("BoatFlyCheck",      cfg.boatFlyCheckEnabled));
        send(source, row("ElytraFlyCheck",    cfg.elytraFlyCheckEnabled));
        send(source, row("StepCheck",         cfg.stepCheckEnabled));
        send(source, row("GroundSpoofCheck",  cfg.groundSpoofCheckEnabled));
        send(source, row("TeleportCheck",    cfg.teleportCheckEnabled));

        send(source, " §8§oCombat");
        send(source, row("ReachCheck",        cfg.reachCheckEnabled));
        send(source, row("KillAuraCheck",     cfg.killAuraCheckEnabled));
        send(source, row("GhostTrapCheck",    cfg.ghostTrapCheckEnabled));
        send(source, row("CriticalsCheck",    cfg.criticalsCheckEnabled));
        send(source, row("VelocityCheck",     cfg.velocityCheckEnabled));
        send(source, row("RotationCheck",     cfg.rotationCheckEnabled));
        send(source, row("PostKillSnapCheck", cfg.postKillSnapCheckEnabled));

        send(source, " §8§oWorld");
        send(source, row("ScaffoldCheck",     cfg.scaffoldCheckEnabled));
        send(source, row("FastBreakCheck",    cfg.fastBreakCheckEnabled));
        send(source, row("FastPlaceCheck",    cfg.fastPlaceCheckEnabled));
        send(source, row("TowerCheck",        cfg.towerCheckEnabled));
        send(source, row("NoFallCheck",       cfg.noFallCheckEnabled));

        send(source, " §8§oClient");
        send(source, row("AutoClickerCheck",  cfg.autoClickerCheckEnabled));
        send(source, row("AutoTotemCheck",    cfg.autoTotemCheckEnabled));
        send(source, row("InventoryCheck",    cfg.inventoryCheckEnabled));
        send(source, row("TimerCheck",        cfg.timerCheckEnabled));
        send(source, row("BadPacketsCheck",   cfg.badPacketsCheckEnabled));

        send(source, " §8§oSystem");
        send(source, row("Logging",           cfg.enableLogging));
        send(source, row("StaffAlerts",       cfg.enableStaffAlerts));
        send(source, row("Discord",           cfg.enableDiscordWebhook));
        send(source, row("WebDashboard",      cfg.enableWebDashboard));

        if (cfg.enableWebDashboard) {
            send(source, BULLET + "§8» §7Dashboard: §bhttp://127.0.0.1:" + cfg.webDashboardPort + "/");
        }
        send(source, BULLET + "§7Version: §e" + Praxic.VERSION +
                " §8| §7Evidence: §e" + Praxic.getEvidenceManager().count() + " records");
        send(source, LINE);
        return 1;
    }

    private static int cmdDashboard(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PraxicConfig cfg = Praxic.getConfig();
        if (!cfg.enableWebDashboard) {
            source.sendFailure(Component.literal(
                    "§c[PRAXIC] Web dashboard is disabled. Set enableWebDashboard: true in config and restart."));
            return 0;
        }
        send(source, "§6[PRAXIC] §fAdmin dashboard: §bhttp://127.0.0.1:" + cfg.webDashboardPort + "/");
        return 1;
    }

    private static int cmdStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int totalFlags = PraxicStats.getTotalFlags();
        Map<String, Integer> topChecks = PraxicStats.getTopChecks(5);
        Map<String, Integer> topPlayers = PraxicStats.getTopPlayers(5);

        send(source, HEADER);
        send(source, BULLET + "§7Session statistics:");
        send(source, LINE);
        send(source, BULLET + "§7Total flags §8— §e" + totalFlags);
        send(source, BULLET + "§7Evidence records §8— §e" + Praxic.getEvidenceManager().count());

        send(source, BULLET + "§7Top checks:");
        if (topChecks.isEmpty()) {
            send(source, "   §8No data yet.");
        } else {
            topChecks.forEach((check, count) -> send(source,
                    "   §8— §b" + check + " §8(" + count + "§8)"));
        }

        send(source, BULLET + "§7Top players:");
        if (topPlayers.isEmpty()) {
            send(source, "   §8No data yet.");
        } else {
            topPlayers.forEach((name, count) -> send(source,
                    "   §8— §e" + name + " §8(" + count + " flags§8)"));
        }

        send(source, LINE);
        return 1;
    }

    private static int cmdPerf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double mspt = CheckManager.getCurrentMspt();
        double tps = CheckManager.getCurrentTps();
        long lastNs = CheckManager.getLastTickNanos();
        double avgNs = CheckManager.getAvgTickNanos();
        double lastMs = lastNs / 1_000_000.0;
        double avgMs = avgNs / 1_000_000.0;
        int playerCount = Praxic.getCheckManager().getAllData().size();
        double perPlayerMs = playerCount > 0 ? avgMs / playerCount : 0;

        String tpsColor = tps >= 19.0 ? "§a" : tps >= 15.0 ? "§e" : "§c";
        String loadColor = avgMs < 2.0 ? "§a" : avgMs < 5.0 ? "§e" : "§c";
        // Rough percentage of a 50ms tick budget
        double pct = Math.min(100.0, (avgMs / 50.0) * 100.0);

        send(source, HEADER);
        send(source, BULLET + "§7Server Performance");
        send(source, LINE);
        send(source, BULLET + "§7TPS: " + tpsColor + fmt2(tps) + " §8| §7MSPT: " + tpsColor + fmt2(mspt) + "ms");
        send(source, BULLET + "§7AntiCheat overhead:");
        send(source, "   §8— §7Last tick: " + loadColor + String.format("%.3f", lastMs) + "ms");
        send(source, "   §8— §7Avg tick:  " + loadColor + String.format("%.3f", avgMs) + "ms" +
                " §8(" + String.format("%.1f", pct) + "% of tick budget)");
        send(source, "   §8— §7Per player: " + loadColor + String.format("%.3f", perPlayerMs) + "ms" +
                " §8(" + playerCount + " tracked)");
        send(source, BULLET + "§7Checks: §e" + Praxic.getCheckManager().getChecks().size() +
                " §8| §7Evidence: §e" + Praxic.getEvidenceManager().count());
        send(source, LINE);
        return 1;
    }

    private static int cmdCheck(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }

        UUID uuid = target.getUUID();
        PlayerData data = Praxic.getCheckManager().getPlayerData(uuid);
        if (data == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] No data for: §e" + name));
            return 0;
        }

        double conf = Praxic.getConfidenceEngine().getScore(uuid);
        double anomaly = Praxic.getAnomalyScoreEngine().getScore(uuid);
        PlayerAnalytics analytics = Praxic.getCheckManager().getAnalytics(uuid);
        PlayerBaseline baseline = Praxic.getCheckManager().getPlayerBaseline(uuid);
        int ghostCount = Praxic.getGhostEntityManager() != null
                ? Praxic.getGhostEntityManager().getActiveGhostCount(uuid) : 0;

        send(source, HEADER);
        send(source, BULLET + "§7Player: §e" + name +
                " §8| Anomaly: §e" + fmt2(anomaly) +
                " §8| Ghosts: §e" + ghostCount);
        send(source, BULLET + "§7Confidence: " + confidenceBar(conf) + " §e" + fmt2(conf));
        send(source, LINE);

        if (data.violations.isEmpty()) {
            send(source, BULLET + "§aNo violations recorded.");
        } else {
            data.violations.forEach((check, count) -> {
                String color = count >= 5 ? "§c" : count >= 3 ? "§e" : "§a";
                send(source, BULLET + "§f" + check + " §8— " + color + count + " VL");
            });
        }

        if (analytics != null) {
            send(source, LINE);
            send(source, BULLET + "§7Analytics: " +
                    "§bEntropy §f" + fmt2(analytics.rotation.entropy) +
                    " §8| §bSnap §f" + fmt2(analytics.rotation.maxSnapAngle) + "°" +
                    " §8| §bCPS §f" + fmt2(analytics.timing.avgCps) +
                    " §8| §bSpeed §f" + fmt2(analytics.movement.avgSpeed));
            send(source, BULLET + "§7Movement: " +
                    "§bState §f" + data.movementState +
                    " §8| §bAir §f" + data.airTicks +
                    " §8| §bNoSlowBuf §f" + data.noSlowBuffer +
                    " §8| §bPhase §f" + data.phaseTicks);
        }

        if (baseline != null) {
            String status = baseline.baselineReady ? "§aREADY" : "§eWARMING";
            send(source, BULLET + "§7Baseline: " + status +
                    " §8(" + baseline.baselineTicksCollected + "/" + baseline.baselineTicksRequired +
                    " ticks, dev: §e" + fmt2(baseline.deviationScore) + "§8)");
        }

        send(source, LINE);
        return 1;
    }

    private static int cmdViolations(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Map<UUID, PlayerData> allData = Praxic.getCheckManager().getAllData();

        send(source, HEADER);
        send(source, BULLET + "§7All player violations:");
        send(source, LINE);

        boolean[] any = {false};
        allData.forEach((uuid, pData) -> {
            if (!pData.violations.isEmpty()) {
                ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                String playerName = p != null ? p.getName().getString() : uuid.toString();
                send(source, BULLET + "§e" + playerName + " §8— §7" + pData.violations);
                any[0] = true;
            }
        });

        if (!any[0]) send(source, BULLET + "§aNo violations recorded.");
        send(source, LINE);
        return 1;
    }

    private static int cmdReset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }

        UUID uuid = target.getUUID();
        PlayerData data = Praxic.getCheckManager().getPlayerData(uuid);
        if (data == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] No data for: §e" + name));
            return 0;
        }

        data.violations.clear();
        data.lastFlagTime.clear();
        Praxic.getConfidenceEngine().reset(uuid);
        Praxic.getAnomalyScoreEngine().reset(uuid);

        send(source, "§6[PRAXIC] §fViolations, confidence and anomaly for §e" + name + " §fcleared.");
        Praxic.LOGGER.info("[PRAXIC] Violations reset for {} by {}", name, source.getTextName());
        PraxicLogger.logInfo("Violations reset for " + name + " by " + source.getTextName());
        return 1;
    }

    private static int cmdDebug(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }
        UUID uuid = target.getUUID();
        if (DebugRecorder.isRecording(uuid)) {
            int remaining = DebugRecorder.ticksRemaining(uuid);
            send(source, "§6[PRAXIC] §e" + name + " §7is already being recorded. " +
                    "§8(" + remaining + " ticks remaining, §7" + (remaining / 20) + "s§8)");
            return 0;
        }
        boolean started = DebugRecorder.start(uuid, name);
        if (started) {
            send(source, "§6[PRAXIC] §aRecording §e" + name + " §afor 30 seconds §8(600 ticks)§a.");
            send(source, "   §7Output: §8config/praxic-debug/debug-" + name + "-<timestamp>.json");
        }
        return 1;
    }

    private static int cmdTpFlag(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        UUID uuid = findUuid(source, name);
        if (uuid == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] This command must be run by a player."));
            return 0;
        }
        // Get the most recent evidence entry for this player
        List<EvidenceManager.EvidenceEntry> entries = Praxic.getEvidenceManager().getRecent(uuid, 1);
        if (entries.isEmpty()) {
            source.sendFailure(Component.literal("§c[PRAXIC] No flag location recorded for §e" + name));
            return 0;
        }
        EvidenceManager.EvidenceEntry last = entries.get(0);
        executor.connection.teleport(last.x, last.y, last.z,
                executor.getYRot(), executor.getXRot(), Set.of());
        send(source, "§6[PRAXIC] §fTeleported to last flag location for §e" + name +
                " §8(" + String.format("%.1f %.1f %.1f", last.x, last.y, last.z) +
                " §8in §7" + last.world + "§8)");
        return 1;
    }

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Praxic.reloadConfig();
        send(source, "§6[PRAXIC] §fConfig §areloaded§f.");
        Praxic.LOGGER.info("[PRAXIC] Config reloaded by {}", source.getTextName());
        return 1;
    }

    private static int cmdWhitelistAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }
        WhitelistManager wl = Praxic.getWhitelistManager();
        boolean added = wl.add(target.getUUID());
        if (added) {
            send(source, "§6[PRAXIC] §e" + name + " §fadded to whitelist.");
            PraxicLogger.logInfo(name + " added to whitelist by " + source.getTextName());
        } else {
            source.sendFailure(Component.literal("§c[PRAXIC] §e" + name + " §fis already whitelisted."));
        }
        return 1;
    }

    private static int cmdWhitelistRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }
        WhitelistManager wl = Praxic.getWhitelistManager();
        boolean removed = wl.remove(target.getUUID());
        if (removed) {
            send(source, "§6[PRAXIC] §e" + name + " §fremoved from whitelist.");
            PraxicLogger.logInfo(name + " removed from whitelist by " + source.getTextName());
        } else {
            source.sendFailure(Component.literal("§c[PRAXIC] §e" + name + " §fis not whitelisted."));
        }
        return 1;
    }

    private static int cmdWhitelistList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<UUID> all = Praxic.getWhitelistManager().getAll();
        send(source, HEADER);
        send(source, BULLET + "§7Whitelist §8(" + all.size() + " entries)§7:");
        send(source, LINE);
        if (all.isEmpty()) {
            send(source, BULLET + "§aNo players whitelisted.");
        } else {
            all.forEach(uuid -> {
                ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                String displayName = p != null ? p.getName().getString() : uuid.toString();
                String dot = p != null ? "§a● " : "§8○ ";
                send(source, BULLET + dot + "§f" + displayName);
            });
        }
        send(source, LINE);
        return 1;
    }

    private static int cmdHistory(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        UUID uuid = findUuid(source, name);
        if (uuid == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }

        List<HistoryManager.ViolationEntry> entries = Praxic.getHistoryManager().getHistory(uuid);
        send(source, HEADER);
        send(source, BULLET + "§7History for §e" + name + " §8(" + entries.size() + " entries)§7:");
        send(source, LINE);

        if (entries.isEmpty()) {
            send(source, BULLET + "§aNo history recorded.");
        } else {
            int start = Math.max(0, entries.size() - 10);
            List<HistoryManager.ViolationEntry> recent = entries.subList(start, entries.size());
            for (int i = recent.size() - 1; i >= 0; i--) {
                HistoryManager.ViolationEntry e = recent.get(i);
                String actionColor = actionColor(e.action);
                send(source, BULLET + "§8[§7" + e.timestamp + "§8] " +
                        "§b" + e.check + " §8VL:" + e.vl +
                        " " + actionColor + e.action +
                        " §8— §7" + e.details);
            }
        }
        send(source, LINE);
        return 1;
    }

    private static int cmdEvidenceGlobal(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<EvidenceManager.EvidenceEntry> entries = Praxic.getEvidenceManager().getRecent(10);
        send(source, HEADER);
        send(source, BULLET + "§7Recent evidence §8(last " + entries.size() + ")§7:");
        send(source, LINE);
        sendEvidenceEntries(source, entries, true);
        send(source, LINE);
        return 1;
    }

    private static int cmdEvidencePlayer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        UUID uuid = findUuid(source, name);
        if (uuid == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }

        List<EvidenceManager.EvidenceEntry> entries = Praxic.getEvidenceManager().getRecent(uuid, 10);
        send(source, HEADER);
        send(source, BULLET + "§7Evidence for §e" + name + " §8(last " + entries.size() + ")§7:");
        send(source, LINE);
        sendEvidenceEntries(source, entries, false);
        send(source, LINE);
        return 1;
    }

    private static int cmdEvidenceClear(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        UUID uuid = findUuid(source, name);
        if (uuid == null) {
            source.sendFailure(Component.literal("§c[PRAXIC] Player not found: §e" + name));
            return 0;
        }
        Praxic.getEvidenceManager().clear(uuid);
        send(source, "§6[PRAXIC] §fEvidence for §e" + name + " §fcleared.");
        PraxicLogger.logInfo("Evidence cleared for " + name + " by " + source.getTextName());
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void sendEvidenceEntries(CommandSourceStack source,
                                            List<EvidenceManager.EvidenceEntry> entries,
                                            boolean includePlayer) {
        if (entries.isEmpty()) {
            send(source, BULLET + "§aNo evidence recorded.");
            return;
        }
        for (EvidenceManager.EvidenceEntry e : entries) {
            String playerPart = includePlayer ? "§e" + e.playerName + " §8— " : "";
            send(source, BULLET + "§8[§7" + e.timestamp + "§8] " + playerPart +
                    "§b" + e.check + " §8VL:" + e.vl +
                    " " + actionColor(e.action) + e.action +
                    " §8| §7Conf §e" + fmt2(e.confidence) +
                    " §8| §7Ping §e" + e.ping + "ms" +
                    " §8| §7" + e.world + " " + fmt2(e.x) + " " + fmt2(e.y) + " " + fmt2(e.z));
            send(source, "   §8↳ §7" + e.details);
        }
    }

    private static UUID findUuid(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();

        GameProfile profile = source.getServer().getProfileCache() != null
                ? source.getServer().getProfileCache().get(name).map(p -> p).orElse(null)
                : null;
        return profile != null ? profile.getId() : null;
    }

    private static String row(String label, boolean enabled) {
        return BULLET + "§e" + label + " §8— " + (enabled ? ON : OFF);
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static String fmt2(double value) {
        return String.format("%.2f", value);
    }

    /**
     * Renders a 10-segment confidence bar with colour coding.
     * Green (0.0–0.29), yellow (0.30–0.59), red (0.60–1.0).
     */
    private static String confidenceBar(double confidence) {
        int filled = (int) Math.round(Math.max(0, Math.min(1.0, confidence)) * 10);
        String color = confidence >= 0.60 ? "§c" : confidence >= 0.30 ? "§e" : "§a";
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? color + "█" : "§8░");
        }
        sb.append("§8]");
        return sb.toString();
    }

    private static String actionColor(String action) {
        return switch (action) {
            case "ban"     -> "§c";
            case "kick"    -> "§e";
            case "freeze"  -> "§9";
            case "setback" -> "§b";
            case "warn"    -> "§6";
            default        -> "§7";
        };
    }
}

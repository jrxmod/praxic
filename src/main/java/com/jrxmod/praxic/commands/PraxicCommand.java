package com.jrxmod.praxic.commands;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class PraxicCommand {

    // Reusable UI elements
    private static final String LINE = "§8§m──────────────────────────§r";
    private static final String HEADER = "§8§m────§r §6§lPRAXIC§r §8§m────§r";
    private static final String ENABLED = "§a✔ Enabled";
    private static final String DISABLED = "§c✘ Disabled";
    private static final String BULLET = " §8» §r";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(Commands.literal("praxic")
                    .requires(source -> source.hasPermission(2))

                    // /praxic status
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                PraxicConfig cfg = Praxic.getConfig();

                                Praxic.LOGGER.info("[PRAXIC] Status: Fly={} Speed={} NoFall={} Reach={} KillAura={} Scaffold={} AutoTotem={} Inventory={} AutoClicker={} Timer={} FastBreak={} Logging={}",
                                        cfg.flyCheckEnabled ? "ON" : "OFF",
                                        cfg.speedCheckEnabled ? "ON" : "OFF",
                                        cfg.noFallCheckEnabled ? "ON" : "OFF",
                                        cfg.reachCheckEnabled ? "ON" : "OFF",
                                        cfg.killAuraCheckEnabled ? "ON" : "OFF",
                                        cfg.scaffoldCheckEnabled ? "ON" : "OFF",
                                        cfg.autoTotemCheckEnabled ? "ON" : "OFF",
                                        cfg.inventoryCheckEnabled ? "ON" : "OFF",
                                        cfg.autoClickerCheckEnabled ? "ON" : "OFF",
                                        cfg.timerCheckEnabled ? "ON" : "OFF",
                                        cfg.fastBreakCheckEnabled ? "ON" : "OFF",
                                        cfg.enableLogging ? "ON" : "OFF");

                                source.sendSuccess(() -> Component.literal(HEADER), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7FlyCheck          " + (cfg.flyCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7SpeedCheck        " + (cfg.speedCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7NoFallCheck       " + (cfg.noFallCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7ReachCheck        " + (cfg.reachCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7KillAuraCheck     " + (cfg.killAuraCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7ScaffoldCheck     " + (cfg.scaffoldCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7AutoTotemCheck    " + (cfg.autoTotemCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7InventoryCheck    " + (cfg.inventoryCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7AutoClickerCheck  " + (cfg.autoClickerCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7TimerCheck        " + (cfg.timerCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7FastBreakCheck    " + (cfg.fastBreakCheckEnabled ? ENABLED : DISABLED)), false);
                                source.sendSuccess(() -> Component.literal(BULLET + "§7Logging           " + (cfg.enableLogging ? ENABLED : DISABLED)), false);
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
                                            source.sendFailure(Component.literal("§c[PRAXIC] §fPlayer not found: §e" + name));
                                            return 0;
                                        }

                                        PlayerData data = Praxic.getCheckManager()
                                                .getPlayerData(target.getUUID());

                                        if (data == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] §fNo data for: §e" + name));
                                            return 0;
                                        }

                                        Praxic.LOGGER.info("[PRAXIC] Violations for {}: {}", name, data.violations);

                                        source.sendSuccess(() -> Component.literal(HEADER), false);
                                        source.sendSuccess(() -> Component.literal(" §7Violations for §e§l" + name + "§r§7:"), false);
                                        source.sendSuccess(() -> Component.literal(LINE), false);

                                        if (data.violations.isEmpty()) {
                                            source.sendSuccess(() -> Component.literal(BULLET + "§aNo violations recorded."), false);
                                        } else {
                                            data.violations.forEach((check, count) -> {
                                                String color = count >= 5 ? "§c" : count >= 3 ? "§e" : "§a";
                                                source.sendSuccess(() -> Component.literal(
                                                        BULLET + "§7" + check + " §8— " + color + count + " VL"), false);
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
                                source.sendSuccess(() -> Component.literal(" §7All player violations:"), false);
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
                                            source.sendFailure(Component.literal("§c[PRAXIC] §fPlayer not found: §e" + name));
                                            return 0;
                                        }

                                        PlayerData data = Praxic.getCheckManager()
                                                .getPlayerData(target.getUUID());

                                        if (data == null) {
                                            source.sendFailure(Component.literal("§c[PRAXIC] §fNo data for: §e" + name));
                                            return 0;
                                        }

                                        data.violations.clear();
                                        data.lastFlagTime.clear();

                                        source.sendSuccess(() -> Component.literal(
                                                "§6[PRAXIC] §fViolations for §e" + name + " §fhave been §acleared§f."), false);
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
                                        "§6[PRAXIC] §fConfig §areloaded §fsuccessfully!"), false);
                                Praxic.LOGGER.info("[PRAXIC] Config reloaded by {}", source.getTextName());
                                return 1;
                            }))
            );
        });
    }
}

package com.jrxmod.praxic.commands;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class PraxicCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(Commands.literal("praxic")
                    .requires(source -> source.hasPermission(2))

                    // /praxic status
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                PraxicConfig cfg = Praxic.getConfig();

                                Praxic.LOGGER.info("[PRAXIC] Status: Fly={} Speed={} NoFall={} Reach={} KillAura={} Logging={}",
                                        cfg.flyCheckEnabled ? "ON" : "OFF",
                                        cfg.speedCheckEnabled ? "ON" : "OFF",
                                        cfg.noFallCheckEnabled ? "ON" : "OFF",
                                        cfg.reachCheckEnabled ? "ON" : "OFF",
                                        cfg.killAuraCheckEnabled ? "ON" : "OFF",
                                        cfg.enableLogging ? "ON" : "OFF");

                                source.sendSuccess(() -> Component.literal("§6[PRAXIC] §fStatus:"), false);
                                source.sendSuccess(() -> Component.literal("§7FlyCheck: " + (cfg.flyCheckEnabled ? "§aEnabled" : "§cDisabled")), false);
                                source.sendSuccess(() -> Component.literal("§7SpeedCheck: " + (cfg.speedCheckEnabled ? "§aEnabled" : "§cDisabled")), false);
                                source.sendSuccess(() -> Component.literal("§7NoFallCheck: " + (cfg.noFallCheckEnabled ? "§aEnabled" : "§cDisabled")), false);
                                source.sendSuccess(() -> Component.literal("§7ReachCheck: " + (cfg.reachCheckEnabled ? "§aEnabled" : "§cDisabled")), false);
                                source.sendSuccess(() -> Component.literal("§7KillAuraCheck: " + (cfg.killAuraCheckEnabled ? "§aEnabled" : "§cDisabled")), false);
                                source.sendSuccess(() -> Component.literal("§7Logging: " + (cfg.enableLogging ? "§aEnabled" : "§cDisabled")), false);
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
                                            source.sendFailure(Component.literal("[PRAXIC] Player not found: " + name));
                                            return 0;
                                        }

                                        PlayerData data = Praxic.getCheckManager()
                                                .getPlayerData(target.getUUID());

                                        if (data == null) {
                                            source.sendFailure(Component.literal("[PRAXIC] No data for: " + name));
                                            return 0;
                                        }

                                        Praxic.LOGGER.info("[PRAXIC] Violations for {}: {}", name, data.violations);

                                        source.sendSuccess(() -> Component.literal("§6[PRAXIC] §fViolations for §e" + name + "§f:"), false);
                                        if (data.violations.isEmpty()) {
                                            source.sendSuccess(() -> Component.literal("§7  No violations recorded."), false);
                                        } else {
                                            data.violations.forEach((check, count) ->
                                                    source.sendSuccess(() -> Component.literal("§7  " + check + ": §c" + count), false));
                                        }
                                        return 1;
                                    })))

                    // /praxic violations
                    .then(Commands.literal("violations")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                Map<UUID, PlayerData> allData = Praxic.getCheckManager().getAllData();

                                source.sendSuccess(() -> Component.literal("§6[PRAXIC] §fAll violations:"), false);
                                boolean[] any = {false};
                                allData.forEach((uuid, pData) -> {
                                    if (!pData.violations.isEmpty()) {
                                        ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                                        String playerName = p != null ? p.getName().getString() : uuid.toString();
                                        Praxic.LOGGER.info("[PRAXIC] {} -> {}", playerName, pData.violations);
                                        source.sendSuccess(() -> Component.literal("§e" + playerName + "§7: " + pData.violations), false);
                                        any[0] = true;
                                    }
                                });
                                if (!any[0]) source.sendSuccess(() -> Component.literal("§7  No violations recorded."), false);
                                return 1;
                            }))

                    // /praxic reload
                    .then(Commands.literal("reload")
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                PraxicConfig.load();
                                source.sendSuccess(() -> Component.literal("§6[PRAXIC] §fConfig reloaded! Restart server to apply all changes."), false);
                                Praxic.LOGGER.info("[PRAXIC] Config reload requested by {}", source.getTextName());
                                return 1;
                            }))
            );
        });
    }
}

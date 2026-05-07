package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.checks.*;
import com.jrxmod.praxic.data.PlayerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class CheckManager {

    private final List<AbstractCheck> checks = new ArrayList<>();
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    public CheckManager() {
        checks.add(new FlyCheck());
        checks.add(new SpeedCheck());
        checks.add(new NoFallCheck());
        checks.add(new ReachCheck());
        checks.add(new KillAuraCheck());
        checks.add(new ScaffoldCheck());
        checks.add(new AutoTotemCheck());
        checks.add(new InventoryCheck());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
                PlayerData data = getOrCreateData(player);
                runChecks(player, data);
                // Update position after checks
                data.updatePosition(player.getX(), player.getY(), player.getZ());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            playerDataMap.put(player.getUUID(),
                    new PlayerData(player.getX(), player.getY(), player.getZ()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerDataMap.remove(handler.getPlayer().getUUID());
        });
    }

    private void runChecks(ServerPlayer player, PlayerData data) {
        for (AbstractCheck check : checks) {
            check.check(player, data);
        }
    }

    private PlayerData getOrCreateData(ServerPlayer player) {
        return playerDataMap.computeIfAbsent(player.getUUID(),
                uuid -> new PlayerData(player.getX(), player.getY(), player.getZ()));
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public List<AbstractCheck> getChecks() {
        return checks;
    }

    public Map<UUID, PlayerData> getAllData() {
        return playerDataMap;
    }
}

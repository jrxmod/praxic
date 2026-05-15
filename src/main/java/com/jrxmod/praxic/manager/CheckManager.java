package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.*;
import com.jrxmod.praxic.data.PlayerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class CheckManager {

    private final List<AbstractCheck> checks = new ArrayList<>();
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    // Decay fires every 100 ticks (5 seconds at 20 TPS)
    private static final int DECAY_INTERVAL_TICKS = 100;
    private static final long DECAY_INTERVAL_MS = 5000L;
    private int decayTickCounter = 0;

    public CheckManager() {
        checks.add(new FlyCheck());
        checks.add(new SpeedCheck());
        checks.add(new NoFallCheck());
        checks.add(new ReachCheck());
        checks.add(new KillAuraCheck());
        checks.add(new ScaffoldCheck());
        checks.add(new AutoTotemCheck());
        checks.add(new InventoryCheck());
        checks.add(new AutoClickerCheck());
        checks.add(new TimerCheck());
        checks.add(new FastBreakCheck());
        checks.add(new JesusCheck());
        checks.add(new VelocityCheck());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            decayTickCounter++;
            boolean doDecay = decayTickCounter >= DECAY_INTERVAL_TICKS;
            if (doDecay) decayTickCounter = 0;

            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
                PlayerData data = getOrCreateData(player);

                // Decay VL before running checks to keep thresholds fair
                if (doDecay) {
                    data.decayViolations(DECAY_INTERVAL_MS);
                }

                // Skip all checks for whitelisted players
                if (!Praxic.getWhitelistManager().isWhitelisted(player.getUUID())) {
                    runChecks(player, data);
                }

                // Track last safe position for setback action
                if (player.onGround() && !player.isDeadOrDying()) {
                    data.lastSafeX = player.getX();
                    data.lastSafeY = player.getY();
                    data.lastSafeZ = player.getZ();
                }

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

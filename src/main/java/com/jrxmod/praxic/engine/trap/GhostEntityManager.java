package com.jrxmod.praxic.engine.trap;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.GhostTrapCheck;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GhostEntityManager {

    private static final GhostTrapCheck GHOST_TRAP_CHECK = new GhostTrapCheck();

    private final Map<UUID, List<GhostEntity>> activeGhosts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSpawnTime = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public GhostEntityManager() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            PraxicConfig cfg = Praxic.getConfig();
            if (cfg == null) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                cleanupExpiredGhosts(uuid, now, cfg.ghostTrapLifetimeMs);

                if (!cfg.ghostTrapCheckEnabled) continue;
                if (!isEligibleForTrap(player)) continue;
                if (Praxic.getWhitelistManager() != null
                        && Praxic.getWhitelistManager().isWhitelisted(uuid)) continue;

                double chance = Math.max(0.0, Math.min(1.0, cfg.ghostTrapSpawnChance));
                if (shouldSpawnGhost(uuid, now, cfg.ghostTrapSpawnCooldownMs)
                        && random.nextDouble() < chance) {
                    spawnGhostNearPlayer(player);
                    lastSpawnTime.put(uuid, now);
                }
            }
        });
    }

    private boolean isEligibleForTrap(ServerPlayer player) {
        if (player.isDeadOrDying()) return false;
        if (player.isSpectator()) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        return !player.getAbilities().mayfly;
    }

    private boolean shouldSpawnGhost(UUID uuid, long now, long cooldownMs) {
        Long last = lastSpawnTime.get(uuid);
        return last == null || (now - last) > Math.max(5_000L, cooldownMs);
    }

    private void cleanupExpiredGhosts(UUID uuid, long now, long lifetimeMs) {
        List<GhostEntity> ghosts = activeGhosts.get(uuid);
        if (ghosts == null) return;

        long maxLifetime = Math.max(5_000L, lifetimeMs);
        Iterator<GhostEntity> it = ghosts.iterator();
        while (it.hasNext()) {
            GhostEntity ghost = it.next();
            if (!ghost.isActive() || (now - ghost.getSpawnTime()) > maxLifetime) {
                ghost.despawn();
                it.remove();
            }
        }
        if (ghosts.isEmpty()) activeGhosts.remove(uuid);
    }

    public void spawnGhostNearPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            Vec3 pos = player.position().add(
                    (random.nextDouble() - 0.5) * 2.5,
                    1.2,
                    (random.nextDouble() - 0.5) * 2.5
            );

            GhostEntity ghost = new GhostEntity(level, pos);
            activeGhosts.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(ghost);

            Praxic.LOGGER.info("[PRAXIC] Spawned ghost honeypot near {}", player.getName().getString());
        }
    }

    public boolean onPlayerAttack(ServerPlayer player, UUID targetUuid, PlayerData data) {
        if (!Praxic.getConfig().ghostTrapCheckEnabled) return false;

        List<GhostEntity> ghosts = activeGhosts.get(player.getUUID());
        if (ghosts == null) return false;

        Iterator<GhostEntity> it = ghosts.iterator();
        while (it.hasNext()) {
            GhostEntity ghost = it.next();
            if (ghost.getEntity() != null && ghost.getEntity().getUUID().equals(targetUuid)) {
                Praxic.LOGGER.warn("[PRAXIC] Ghost honeypot hit by {} — definitive KillAura evidence",
                        player.getName().getString());
                ghost.despawn();
                it.remove();
                ViolationManager.flag(player, data, GHOST_TRAP_CHECK,
                        "Hit invisible honeypot entity (definitive KillAura evidence)");
                if (ghosts.isEmpty()) activeGhosts.remove(player.getUUID());
                return true;
            }
        }
        return false;
    }

    public int getActiveGhostCount(UUID uuid) {
        List<GhostEntity> list = activeGhosts.get(uuid);
        return list != null ? list.size() : 0;
    }

    public void resetPlayer(UUID uuid) {
        List<GhostEntity> ghosts = activeGhosts.remove(uuid);
        if (ghosts != null) for (GhostEntity g : ghosts) g.despawn();
        lastSpawnTime.remove(uuid);
    }
}

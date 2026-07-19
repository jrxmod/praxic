package com.jrxmod.praxic.engine.trap;

import com.jrxmod.praxic.Praxic;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ghost (honeypot) entities for advanced KillAura and AimAssist detection.
 * Entities are invisible and invulnerable. Attacks on them are considered definitive proof of cheating.
 */
public class GhostEntityManager {

    private static final int MAX_GHOSTS_PER_PLAYER = 3;
    private static final long GHOST_LIFETIME_MS = 30_000L; // 30 seconds
    private static final long SPAWN_COOLDOWN_MS = 45_000L; // 45 seconds between spawns for same player

    private final Map<UUID, List<GhostEntity>> activeGhosts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSpawnTime = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public GhostEntityManager() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();

                // Cleanup expired ghosts
                cleanupExpiredGhosts(uuid, now);

                // Occasionally spawn new ghosts near active players
                if (shouldSpawnGhost(uuid, now) && random.nextFloat() < 0.08f) {
                    spawnGhostNearPlayer(player);
                    lastSpawnTime.put(uuid, now);
                }
            }
        });
    }

    private boolean shouldSpawnGhost(UUID uuid, long now) {
        Long last = lastSpawnTime.get(uuid);
        if (last == null) return true;
        return (now - last) > SPAWN_COOLDOWN_MS;
    }

    private void cleanupExpiredGhosts(UUID uuid, long now) {
        List<GhostEntity> ghosts = activeGhosts.get(uuid);
        if (ghosts == null) return;

        Iterator<GhostEntity> it = ghosts.iterator();
        while (it.hasNext()) {
            GhostEntity ghost = it.next();
            if (!ghost.isActive() || (now - ghost.getSpawnTime()) > GHOST_LIFETIME_MS) {
                ghost.despawn();
                it.remove();
            }
        }

        if (ghosts.isEmpty()) {
            activeGhosts.remove(uuid);
        }
    }

    public void spawnGhostNearPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            Vec3 pos = player.position().add(
                    (random.nextDouble() - 0.5) * 6,
                    0,
                    (random.nextDouble() - 0.5) * 6
            );

            GhostEntity.GhostType type = GhostEntity.GhostType.values()[
                    random.nextInt(GhostEntity.GhostType.values().length)
            ];

            GhostEntity ghost = new GhostEntity(level, pos, type);

            activeGhosts.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(ghost);

            Praxic.LOGGER.info("[PRAXIC] Spawned ghost entity {} near {}", type, player.getName().getString());
        }
    }

    /**
     * Called when a player attacks an entity.
     * Returns true if the attacked entity was a ghost (honeypot hit).
     */
    public boolean onPlayerAttack(ServerPlayer player, UUID targetUuid) {
        List<GhostEntity> ghosts = activeGhosts.get(player.getUUID());
        if (ghosts == null) return false;

        for (GhostEntity ghost : ghosts) {
            if (ghost.getEntity() != null && ghost.getEntity().getUUID().equals(targetUuid)) {
                // Honeypot hit detected!
                Praxic.LOGGER.warn("[PRAXIC] Ghost honeypot hit by {} — definitive KillAura evidence",
                        player.getName().getString());

                // Remove the ghost immediately
                ghost.despawn();
                ghosts.remove(ghost);
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
        if (ghosts != null) {
            for (GhostEntity g : ghosts) g.despawn();
        }
        lastSpawnTime.remove(uuid);
    }
}
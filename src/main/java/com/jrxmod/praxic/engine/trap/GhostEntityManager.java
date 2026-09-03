package com.jrxmod.praxic.engine.trap;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.GhostTrapCheck;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.manager.ViolationManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Honeypot placement follows how automated combat modules select targets:
 * the default priority is the target that needs the smallest head turn,
 * and the scan usually covers all directions around the player. A trap
 * placed behind the player is therefore never chosen while a visible mob
 * is in front of the crosshair.
 *
 * Placement rules:
 *  - While the player attacks a REAL entity (engaged), the honeypot is kept
 *    behind the player so an honest click aimed at that entity cannot hit it.
 *  - When the player is idle (no real-entity attack for a short window), the
 *    honeypot is relocated to the open air in front of the crosshair, exactly
 *    where an automated combat module will pick it. It is only placed there
 *    if no attackable entity is in front, so an honest player starting to
 *    click at nothing is the only residual case.
 *  - Two hits on the same honeypot within a window are required before a
 *    flag; a single accidental swing only produces a log line.
 */
public class GhostEntityManager {

    private static final GhostTrapCheck GHOST_TRAP_CHECK = new GhostTrapCheck();

    /** At most one live honeypot per player. */
    private static final int MAX_GHOSTS_PER_PLAYER = 1;

    /** A second honeypot hit must arrive within this window to count. */
    private static final long CONFIRM_WINDOW_MS = 10_000L;

    /** No real-entity attack for this long means the player is idle. */
    private static final long IDLE_REPOSITION_MS = 1_500L;

    /** Front placement: distance from the player and vertical offset. */
    private static final double FRONT_RADIUS_MIN = 2.0;
    private static final double FRONT_RADIUS_MAX = 2.8;
    private static final double FRONT_HEIGHT_MIN = 0.8;
    private static final double FRONT_HEIGHT_MAX = 1.2;

    /** Behind placement: distance, vertical offset and cone half-angle. */
    private static final double BEHIND_RADIUS_MIN = 1.5;
    private static final double BEHIND_RADIUS_MAX = 3.0;
    private static final double BEHIND_HEIGHT_MIN = 0.9;
    private static final double BEHIND_HEIGHT_MAX = 1.5;
    private static final double BEHIND_JITTER_DEG = 45.0;

    /** Front placement jitter and the clearance check around the crosshair. */
    private static final double FRONT_JITTER_DEG = 10.0;
    private static final double FRONT_CLEAR_DEG = 15.0;
    private static final double FRONT_CLEAR_DIST = 6.0;

    /** Guard angle: a hit through the honeypot aimed at a real mob is honest. */
    private static final double NEAR_LOOK_DEG = 8.0;
    private static final double NEAR_LOOK_DIST = 6.5;

    private final Map<UUID, List<GhostEntity>> activeGhosts = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entityToOwner = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSpawnTime = new ConcurrentHashMap<>();
    /** First hit time per ghost entity; a second hit within the window flags. */
    private final Map<UUID, Long> ghostFirstHitByUuid = new ConcurrentHashMap<>();
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
                if (Praxic.getWhitelistManager() != null
                        && Praxic.getWhitelistManager().isWhitelisted(uuid)) continue;

                List<GhostEntity> ghosts = activeGhosts.get(uuid);
                if (ghosts != null && !ghosts.isEmpty()) {
                    // Keep the placement in sync with combat state. Front is
                    // wanted while idle, behind while engaged.
                    GhostEntity ghost = ghosts.get(0);
                    boolean engaged = isEngaged(player);
                    if (ghost.isFrontPlaced() == engaged) {
                        relocateGhost(player, ghost, !engaged);
                    }
                    continue;
                }

                if (!isEligibleForTrap(player)) continue;
                double chance = Math.max(0.0, Math.min(1.0, cfg.ghostTrapSpawnChance));
                if (shouldSpawnGhost(uuid, now, cfg.ghostTrapSpawnCooldownMs)
                        && random.nextDouble() < chance) {
                    Vec3 pos = findSpawnPosition(player, false);
                    if (pos != null) {
                        spawnGhost(player, pos, false);
                        lastSpawnTime.put(uuid, now);
                    }
                }
            }
        });
    }

    private boolean isEligibleForTrap(ServerPlayer player) {
        if (player.isDeadOrDying()) return false;
        if (player.isSpectator()) return false;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (player.getAbilities().mayfly) return false;

        PraxicConfig cfg = Praxic.getConfig();
        if (cfg == null) return false;

        // Existing suspicion.
        if (Praxic.getConfidenceEngine() != null
                && Praxic.getConfidenceEngine().getScore(player.getUUID())
                >= cfg.ghostTrapConfidenceThreshold) {
            return true;
        }

        // A recent attack also arms the honeypot: without it, a combat module
        // that never triggers another check would never see a tap.
        PlayerData data = Praxic.getCheckManager() != null
                ? Praxic.getCheckManager().getPlayerData(player.getUUID())
                : null;
        return data != null && data.lastAttackTime > 0
                && System.currentTimeMillis() - data.lastAttackTime < 10_000L;
    }

    /** True while the player is attacking real entities (combat). */
    private static boolean isEngaged(ServerPlayer player) {
        PlayerData data = Praxic.getCheckManager() != null
                ? Praxic.getCheckManager().getPlayerData(player.getUUID())
                : null;
        if (data == null || data.lastRealAttackTimeMs <= 0) return false;
        return System.currentTimeMillis() - data.lastRealAttackTimeMs < IDLE_REPOSITION_MS;
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
                UUID ghostUuid = ghost.getEntityUuid();
                if (ghostUuid != null) ghostFirstHitByUuid.remove(ghostUuid);
                unregister(ghost);
                ghost.despawn();
                it.remove();
            }
        }
        if (ghosts.isEmpty()) activeGhosts.remove(uuid);
    }

    /** Creates + registers a honeypot at the given position and placement. */
    private void spawnGhost(ServerPlayer player, Vec3 pos, boolean front) {
        if (!(player.level() instanceof ServerLevel level)) return;

        GhostEntity ghost = new GhostEntity(level, pos, player.getUUID());
        ghost.setFrontPlaced(front);
        activeGhosts.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(ghost);
        UUID entityUuid = ghost.getEntityUuid();
        if (entityUuid != null) {
            entityToOwner.put(entityUuid, player.getUUID());
        }
        Praxic.LOGGER.debug("[PRAXIC] Spawned ghost honeypot ({}) near {}",
                front ? "front" : "behind", player.getName().getString());
    }

    /**
     * Moves the honeypot to the requested side. The old entity is despawned
     * and a new one is spawned so the client (and any KillAura) sees the new
     * position immediately.
     */
    private void relocateGhost(ServerPlayer player, GhostEntity ghost, boolean front) {
        if (ghost.isFrontPlaced() == front) return;
        Vec3 pos = findSpawnPosition(player, front);
        if (pos == null) return;

        unregister(ghost);
        ghost.despawn();
        List<GhostEntity> ghosts = activeGhosts.get(player.getUUID());
        if (ghosts != null) ghosts.remove(ghost);
        spawnGhost(player, pos, front);
    }

    /**
     * Picks a spot for the honeypot. Front placement is a narrow cone along
     * the look direction at eye height; behind placement is a wide cone
     * opposite to it. Both require open air, line of sight and no other
     * entities nearby; front placement additionally requires an empty area in
     * front so honest players never face it while aiming at a real target.
     */
    private Vec3 findSpawnPosition(ServerPlayer player, boolean front) {
        Vec3 look = player.getLookAngle();
        Vec3 xz = new Vec3(look.x, 0.0, look.z);
        if (xz.lengthSqr() < 1.0E-6) xz = new Vec3(0.0, 0.0, -1.0);
        xz = xz.normalize();
        double aimAngle = Math.atan2(xz.z, xz.x);
        double jitterDeg = front ? FRONT_JITTER_DEG : BEHIND_JITTER_DEG;
        double jitterRad = Math.toRadians(jitterDeg);
        Vec3 base = player.position();

        for (int k = 0; k < 16; k++) {
            double offset = (random.nextDouble() * 2.0 - 1.0) * jitterRad;
            double angle = aimAngle + offset;
            double radius = front
                    ? FRONT_RADIUS_MIN + random.nextDouble() * (FRONT_RADIUS_MAX - FRONT_RADIUS_MIN)
                    : BEHIND_RADIUS_MIN + random.nextDouble() * (BEHIND_RADIUS_MAX - BEHIND_RADIUS_MIN);
            double height = front
                    ? FRONT_HEIGHT_MIN + random.nextDouble() * (FRONT_HEIGHT_MAX - FRONT_HEIGHT_MIN)
                    : BEHIND_HEIGHT_MIN + random.nextDouble() * (BEHIND_HEIGHT_MAX - BEHIND_HEIGHT_MIN);
            Vec3 pos = base.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

            if (!isOpenAir(player.level(), pos)) continue;
            if (!hasLineOfSight(player, pos)) continue;
            if (!isAwayFromEntities(player.level(), player, pos)) continue;
            if (front && !frontClear(player)) continue;
            return pos;
        }
        return null;
    }

    /** Feet and head cells must be air (the stand must not be inside a block). */
    private static boolean isOpenAir(Level level, Vec3 pos) {
        BlockPos foot = BlockPos.containing(pos);
        return level.getBlockState(foot).isAir() && level.getBlockState(foot.above()).isAir();
    }

    /** Clear sight from the player's eye to the honeypot body. */
    private static boolean hasLineOfSight(ServerPlayer player, Vec3 pos) {
        Vec3 from = player.getEyePosition();
        Vec3 to = pos.add(0.0, 0.55, 0.0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player);
        return player.level().clip(ctx).getType() == HitResult.Type.MISS;
    }

    /** True when no entity other than the owner / honeypots is within 2.0 blocks. */
    private boolean isAwayFromEntities(Level level, ServerPlayer owner, Vec3 pos) {
        AABB box = new AABB(pos.x - 2.0, pos.y - 2.0, pos.z - 2.0,
                pos.x + 2.0, pos.y + 2.0, pos.z + 2.0);
        return level.getEntitiesOfClass(Entity.class, box,
                e -> e != null && e != owner && !e.isRemoved()
                        && !isGhostEntity(e.getUUID())).isEmpty();
    }

    /** True when no attackable entity is inside the 15-degree front cone. */
    private boolean frontClear(ServerPlayer player) {
        return !hasRealTargetInCone(player, FRONT_CLEAR_DEG, FRONT_CLEAR_DIST);
    }

    /** True when the player is aiming at a real entity near the honeypot. */
    private boolean hasRealTargetNearLook(ServerPlayer player, GhostEntity ghost) {
        return hasRealTargetInCone(player, NEAR_LOOK_DEG, NEAR_LOOK_DIST);
    }

    /**
     * Scans a cone around the look direction for a real attackable entity
     * (everything a client can hit, except items, orbs and honeypots).
     */
    private boolean hasRealTargetInCone(ServerPlayer player, double coneDeg, double maxDist) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cosLimit = Math.cos(Math.toRadians(coneDeg));
        Level level = player.level();
        AABB box = new AABB(eye.x - maxDist, eye.y - maxDist, eye.z - maxDist,
                eye.x + maxDist, eye.y + maxDist, eye.z + maxDist);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, ent ->
                ent != null && ent != player && !ent.isRemoved())) {
            if (e instanceof ItemEntity || e instanceof ExperienceOrb) continue;
            if (isGhostEntity(e.getUUID())) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > maxDist || dist < 0.05) continue;
            if (to.normalize().dot(look) >= cosLimit) return true;
        }
        return false;
    }

    /**
     * True when {@code entity} is a honeypot that must not be sent to {@code viewer}.
     * The owner still receives spawn/track packets so KillAura can interact.
     */
    public boolean shouldHideFrom(Entity entity, ServerPlayer viewer) {
        if (entity == null || viewer == null) return false;
        UUID owner = entityToOwner.get(entity.getUUID());
        if (owner == null) return false;
        return !owner.equals(viewer.getUUID());
    }

    public boolean isGhostEntity(UUID entityUuid) {
        return entityUuid != null && entityToOwner.containsKey(entityUuid);
    }

    public boolean onPlayerAttack(ServerPlayer player, UUID targetUuid, PlayerData data) {
        if (!Praxic.getConfig().ghostTrapCheckEnabled) return false;

        List<GhostEntity> ghosts = activeGhosts.get(player.getUUID());
        if (ghosts == null || ghosts.isEmpty()) {
            // First attack in combat: arm the honeypot (behind the player).
            spawnOnCombat(player);
            return false;
        }

        long now = System.currentTimeMillis();
        GhostEntity hitGhost = null;
        for (GhostEntity ghost : ghosts) {
            if (ghost.getEntity() != null && ghost.getEntity().getUUID().equals(targetUuid)) {
                hitGhost = ghost;
                break;
            }
        }
        if (hitGhost == null) {
            // Attack on a real entity: mark combat and move the honeypot
            // behind so honest swings aimed at the target can never reach it.
            data.lastRealAttackTimeMs = now;
            relocateGhost(player, ghosts.get(0), false);
            return false;
        }

        // Hit landed on the honeypot. If a real attackable entity is right in
        // the crosshair, the swing was almost certainly aimed at it (the
        // invisible stand is closer); move the stand away and do not count.
        if (hasRealTargetNearLook(player, hitGhost)) {
            relocateGhost(player, hitGhost, false);
            return false;
        }

        UUID ghostUuid = hitGhost.getEntityUuid();
        Long firstHit = ghostFirstHitByUuid.get(ghostUuid);
        if (firstHit == null || now - firstHit > CONFIRM_WINDOW_MS) {
            // First contact: report and wait for a repeat hit. Auto attack
            // keeps swinging; one accidental swing must never ban.
            ghostFirstHitByUuid.put(ghostUuid, now);
            Praxic.LOGGER.info("[PRAXIC] Ghost honeypot touched once by {}  -  awaiting confirmation",
                    player.getName().getString());
            return false;
        }

        Praxic.LOGGER.warn("[PRAXIC] Ghost honeypot hit twice by {}  -  definitive KillAura evidence",
                player.getName().getString());
        unregister(hitGhost);
        hitGhost.despawn();
        ghosts.remove(hitGhost);
        ghostFirstHitByUuid.remove(ghostUuid);
        ViolationManager.flag(player, data, GHOST_TRAP_CHECK,
                "Hit invisible honeypot entity twice (definitive KillAura evidence)");
        if (ghosts.isEmpty()) activeGhosts.remove(player.getUUID());
        return true;
    }

    /**
     * Spawns a honeypot while a player is in active combat, without waiting
     * for the periodic confidence-gated sweep. It is placed behind the player
     * so honest hits on a visible target cannot reach it.
     */
    private void spawnOnCombat(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PraxicConfig cfg = Praxic.getConfig();
        if (cfg == null || !isEligibleForTrap(player)) return;
        if (getActiveGhostCount(uuid) >= MAX_GHOSTS_PER_PLAYER) return;

        long now = System.currentTimeMillis();
        if (!shouldSpawnGhost(uuid, now, cfg.ghostTrapSpawnCooldownMs)) return;

        Vec3 pos = findSpawnPosition(player, false);
        if (pos == null) return;
        spawnGhost(player, pos, false);
        lastSpawnTime.put(uuid, now);
    }

    public int getActiveGhostCount(UUID uuid) {
        List<GhostEntity> list = activeGhosts.get(uuid);
        return list != null ? list.size() : 0;
    }

    public void resetPlayer(UUID uuid) {
        List<GhostEntity> ghosts = activeGhosts.remove(uuid);
        if (ghosts != null) {
            for (GhostEntity g : ghosts) {
                UUID ghostUuid = g.getEntityUuid();
                if (ghostUuid != null) ghostFirstHitByUuid.remove(ghostUuid);
                unregister(g);
                g.despawn();
            }
        }
        lastSpawnTime.remove(uuid);
    }

    private void unregister(GhostEntity ghost) {
        UUID entityUuid = ghost.getEntityUuid();
        if (entityUuid != null) {
            entityToOwner.remove(entityUuid);
            ghostFirstHitByUuid.remove(entityUuid);
        }
    }
}

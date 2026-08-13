package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.*;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.MovementAnalyzer;
import com.jrxmod.praxic.engine.analysis.MovementProfile;
import com.jrxmod.praxic.engine.analysis.PlayerAnalytics;
import com.jrxmod.praxic.engine.analysis.PlayerBaseline;
import com.jrxmod.praxic.engine.analysis.PlayerProfiler;
import com.jrxmod.praxic.engine.analysis.RotationAnalyzer;
import com.jrxmod.praxic.engine.analysis.RotationProfile;
import com.jrxmod.praxic.engine.analysis.TimingAnalyzer;
import com.jrxmod.praxic.engine.analysis.TimingProfile;
import com.jrxmod.praxic.engine.data.PlayerSnapshot;
import com.jrxmod.praxic.engine.data.SnapshotBuilder;
import com.jrxmod.praxic.engine.decision.AnomalyScoreEngine;
import com.jrxmod.praxic.engine.physics.PhysicsEngine;
import com.jrxmod.praxic.engine.physics.PhysicsResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.*;

public class CheckManager {

    private final List<AbstractCheck>        checks         = new ArrayList<>();
    private final Map<UUID, PlayerData>      playerDataMap  = new HashMap<>();
    private final Map<UUID, PlayerSnapshot>  snapshots      = new HashMap<>();
    private final Map<UUID, PhysicsResult>   physicsResults = new HashMap<>();
    private final Map<UUID, PlayerAnalytics> analytics      = new HashMap<>();

    private final PhysicsEngine    physicsEngine    = new PhysicsEngine();
    private final RotationAnalyzer rotationAnalyzer = new RotationAnalyzer();
    private final TimingAnalyzer   timingAnalyzer   = new TimingAnalyzer();
    private final MovementAnalyzer movementAnalyzer = new MovementAnalyzer();
    private final PlayerProfiler   playerProfiler   = new PlayerProfiler();

    // -------------------------------------------------------------------------
    // Direct references to event-driven checks — avoids stream filtering in mixins
    // -------------------------------------------------------------------------

    private final BadPacketsCheck   badPacketsCheck   = new BadPacketsCheck();
    private final TimerCheck        timerCheck        = new TimerCheck();
    private final TeleportCheck     teleportCheck     = new TeleportCheck();
    private final FastBreakCheck    fastBreakCheck    = new FastBreakCheck();
    private final CriticalsCheck    criticalsCheck    = new CriticalsCheck();
    private final ReachCheck        reachCheck        = new ReachCheck();
    private final KillAuraCheck     killAuraCheck     = new KillAuraCheck();
    private final AutoClickerCheck  autoClickerCheck  = new AutoClickerCheck();
    private final InventoryCheck    inventoryCheck    = new InventoryCheck();

    // -------------------------------------------------------------------------
    // Performance monitoring
    // -------------------------------------------------------------------------

    /** Wall-clock timestamp of the previous END_SERVER_TICK. */
    private static long lastTickWallMs = 0;

    /**
     * Measured server tick duration in milliseconds (smoothed).
     * 50.0 at 20 TPS, 100.0 at 10 TPS. Updated every tick.
     */
    private static double currentMspt = 50.0;

    /**
     * Nanosecond timestamp marking the start of the current tick's
     * CheckManager processing. Used by /praxic perf to measure overhead.
     */
    private static long tickStartNanos = 0;

    /** CheckManager processing time for the most recent tick in nanoseconds. */
    private static long lastTickNanos = 0;

    /** Exponentially weighted moving average of tick processing time in nanos. */
    private static double avgTickNanos = 0;

    /** Decay fires every 100 ticks (5 seconds at 20 TPS). */
    private static final int  DECAY_INTERVAL_TICKS = 100;
    private static final long DECAY_INTERVAL_MS    = 5000L;
    private int decayTickCounter = 0;

    /** Grace ticks granted when leaving water, for FlyCheck and JesusCheck. */
    private static final int WATER_EXIT_GRACE_TICKS = 15;

    public CheckManager() {
        checks.add(new FlyCheck());
        checks.add(new YPredictionCheck());
        checks.add(new SpeedCheck());
        checks.add(new PhaseCheck());
        checks.add(new NoSlowCheck());
        checks.add(new NoFallCheck());
        checks.add(reachCheck);
        checks.add(killAuraCheck);
        checks.add(new GhostTrapCheck());
        checks.add(criticalsCheck);
        checks.add(new ScaffoldCheck());
        checks.add(new AutoTotemCheck());
        checks.add(inventoryCheck);
        checks.add(autoClickerCheck);
        checks.add(timerCheck);
        checks.add(badPacketsCheck);
        checks.add(fastBreakCheck);
        checks.add(new JesusCheck());
        checks.add(new VelocityCheck());
        checks.add(new RotationCheck());
        checks.add(new SprintCheck());
        checks.add(new BoatFlyCheck());
        checks.add(new PostKillSnapCheck());
        // New in 0.12.0
        checks.add(new ElytraFlyCheck());
        checks.add(new StepCheck());
        checks.add(new TowerCheck());
        checks.add(new GroundSpoofCheck());
        checks.add(new FastPlaceCheck());
        // New in 0.13.0
        checks.add(teleportCheck);

        // Kill event — notify RotationAnalyzer to open post-kill snap window
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (killer instanceof ServerPlayer player) {
                rotationAnalyzer.onKill(player.getUUID());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Performance monitoring — measure tick interval and own overhead
            long nowWallMs = System.currentTimeMillis();
            if (lastTickWallMs > 0) {
                currentMspt = (currentMspt * 0.8) + ((nowWallMs - lastTickWallMs) * 0.2);
            }
            lastTickWallMs = nowWallMs;
            tickStartNanos = System.nanoTime();

            decayTickCounter++;
            boolean doDecay = decayTickCounter >= DECAY_INTERVAL_TICKS;
            if (doDecay) decayTickCounter = 0;

            long nowMs = System.currentTimeMillis();

            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
                PlayerData data = getOrCreateData(player);
                UUID uuid = player.getUUID();

                // Enforce freeze punishment — hold the player at the frozen
                // position for the remaining freeze ticks.
                if (data.freezeTicksRemaining > 0) {
                    player.connection.teleport(data.freezeX, data.freezeY, data.freezeZ,
                            data.freezeYaw, data.freezePitch, Set.of());
                    data.updatePosition(data.freezeX, data.freezeY, data.freezeZ);
                    data.freezeTicksRemaining--;
                }

                // Track recent damage for AutoTotemCheck (totem consumption context)
                if (player.hurtTime > 0) data.lastDamageTime = nowMs;

                // 1. Decay VL (every 100 ticks) + Confidence decay (every tick after grace)
                if (doDecay) data.decayViolations(DECAY_INTERVAL_MS);
                Praxic.getConfidenceEngine().tickDecay(uuid, nowMs);

                // 2. Skip dead players — death screen causes false positives.
                if (player.getHealth() <= 0) {
                    data.airTicks = 0;
                    data.boatAirTicks = 0;
                    data.elytraAirTicks = 0;
                    data.groundSpoofTicks = 0;
                    data.towerBlockCount = 0;
                    data.fastPlaceCount = 0;
                    data.stepBuffer = 0;
                    data.elytraBuffer = 0;
                    physicsEngine.reset(uuid);
                    data.updatePosition(player.getX(), player.getY(), player.getZ());
                    continue;
                }

                // 3. Compute movement state — single source of truth for all checks
                updateMovementState(player, data);

                // 4. Sync derived legacy fields from the state machine
                syncDerivedFields(player, data);

                // 5. Build immutable snapshot
                PlayerSnapshot snapshot = SnapshotBuilder.build(player, data);
                snapshots.put(uuid, snapshot);

                // 6. Physics simulation
                PhysicsResult physics = physicsEngine.simulate(uuid, snapshot, data);
                physicsResults.put(uuid, physics);

                // 7. Rotation analysis
                RotationProfile rotProfile = rotationAnalyzer.analyse(uuid, snapshot);

                // 8. Timing analysis
                TimingProfile timProfile = timingAnalyzer.analyse(uuid, data);

                // 9. Movement analysis
                MovementProfile movProfile = movementAnalyzer.analyse(uuid, snapshot);

                // 10. Player profiling
                PlayerBaseline baseline = playerProfiler.analyse(uuid, movProfile, rotProfile, timProfile);

                // 11. Aggregate analytics
                PlayerAnalytics analyticsObj = new PlayerAnalytics(rotProfile, timProfile, movProfile, baseline);
                analytics.put(uuid, analyticsObj);

                // 12. Feed anomaly engine — accumulates sub-threshold baseline deviations
                if (baseline.baselineReady && baseline.deviationScore >= 0.0) {
                    AnomalyScoreEngine anomaly = Praxic.getAnomalyScoreEngine();
                    anomaly.feed(uuid, baseline.deviationScore);
                    double anomalyScore = anomaly.getScore(uuid);
                    if (anomalyScore >= AnomalyScoreEngine.NUDGE_THRESHOLD) {
                        Praxic.getConfidenceEngine().nudgeFromAnomaly(uuid, anomalyScore);
                    }
                }

                // 13. Run checks
                if (!Praxic.getWhitelistManager().isWhitelisted(uuid)) {
                    runChecks(player, data);
                }

                // 13b. Debug recorder — capture tick data if recording is active
                DebugRecorder.tick(player);

                // 14. Update safe position
                if (player.onGround() && !player.isDeadOrDying()) {
                    data.lastSafeX = player.getX();
                    data.lastSafeY = player.getY();
                    data.lastSafeZ = player.getZ();
                }

                // 15. Snapshot position and rotation for next tick
                data.updatePosition(player.getX(), player.getY(), player.getZ());
                data.lastYaw   = player.getYRot();
                data.lastPitch = player.getXRot();
            }

            // Performance: record how long CheckManager took this tick
            lastTickNanos = System.nanoTime() - tickStartNanos;
            avgTickNanos = avgTickNanos > 0
                    ? (avgTickNanos * 0.95) + (lastTickNanos * 0.05)
                    : lastTickNanos;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            playerDataMap.put(player.getUUID(),
                    new PlayerData(player.getX(), player.getY(), player.getZ()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            PlayerData data = playerDataMap.get(uuid);

            // Session summary — one-line log entry for retro-analysis
            if (data != null) {
                double confidence = Praxic.getConfidenceEngine().getScore(uuid);
                double anomaly = Praxic.getAnomalyScoreEngine().getScore(uuid);
                int totalVL = data.violations.values().stream().mapToInt(Integer::intValue).sum();
                String topCheck = data.violations.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .max(java.util.Map.Entry.comparingByValue())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .orElse("none");
                int sessionTicks = data.totalTicks;
                int sessionSeconds = sessionTicks / 20;
                Praxic.LOGGER.info("[PRAXIC] Session end: {} | {}m {}s | VL={} conf={} anomaly={} top={}",
                        player.getName().getString(),
                        sessionSeconds / 60, sessionSeconds % 60,
                        totalVL,
                        String.format("%.3f", confidence),
                        String.format("%.3f", anomaly),
                        topCheck);
            }

            playerDataMap.remove(uuid);
            snapshots.remove(uuid);
            physicsResults.remove(uuid);
            analytics.remove(uuid);
            physicsEngine.reset(uuid);
            rotationAnalyzer.reset(uuid);
            timingAnalyzer.reset(uuid);
            movementAnalyzer.reset(uuid);
            playerProfiler.reset(uuid);
            Praxic.getConfidenceEngine().reset(uuid);
            Praxic.getAnomalyScoreEngine().reset(uuid);
            ViolationManager.cleanup(uuid);
            if (Praxic.getGhostEntityManager() != null) {
                Praxic.getGhostEntityManager().resetPlayer(uuid);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Movement State Machine
    // -------------------------------------------------------------------------

    private void updateMovementState(ServerPlayer player, PlayerData data) {
        double dy = player.getY() - data.prevY;
        MovementState next;

        if (player.isInWater()) {
            next = MovementState.WATER;
        } else if (player.onClimbable()) {
            next = MovementState.CLIMB;
        } else if (player.onGround()) {
            next = MovementState.GROUND;
        } else {
            boolean risingFromGround =
                (data.movementState == MovementState.GROUND ||
                 data.movementState == MovementState.JUMP) && dy > 0.0;
            if (risingFromGround) {
                next = MovementState.JUMP;
            } else if (dy < -0.001) {
                next = MovementState.FALLING;
            } else {
                next = MovementState.AIR;
            }
        }

        data.prevMovementState = data.movementState;
        data.movementState     = next;
    }

    private void syncDerivedFields(ServerPlayer player, PlayerData data) {
        MovementState prev = data.prevMovementState;
        MovementState curr = data.movementState;

        data.totalTicks++;
        if (data.joinGraceTicks > 0) data.joinGraceTicks--;

        data.wasOnGround = (prev == MovementState.GROUND);
        data.wasInWater  = (prev == MovementState.WATER);

        boolean airborne = curr == MovementState.JUMP
                        || curr == MovementState.AIR
                        || curr == MovementState.FALLING;
        data.airTicks = airborne ? data.airTicks + 1 : 0;

        boolean justLeftWater = (prev == MovementState.WATER) && (curr != MovementState.WATER);
        if (justLeftWater) {
            data.waterExitTicks       = WATER_EXIT_GRACE_TICKS;
            data.jesusWaterGraceTicks = WATER_EXIT_GRACE_TICKS;
        } else {
            if (data.waterExitTicks       > 0) data.waterExitTicks--;
            if (data.jesusWaterGraceTicks > 0) data.jesusWaterGraceTicks--;
        }

        // Track ground Y for step check
        if (curr == MovementState.GROUND) {
            data.lastGroundY = player.getY();
        }

        // Track elytra state for ElytraFly
        boolean fallFlying = player.isFallFlying();
        if (fallFlying) {
            data.elytraAirTicks++;
        } else {
            if (data.wasFallFlying) {
                data.elytraAirTicks = 0;
            }
        }
        data.wasFallFlying = fallFlying;
        data.lastElytraY = player.getY();

        // GroundSpoof: if packet says onGround but server says airborne for sustained ticks
        if (data.lastPacketHasPos) {
            boolean serverGround = curr == MovementState.GROUND;
            boolean packetGround = data.lastPacketOnGround;
            if (packetGround && !serverGround && data.airTicks > 5) {
                data.groundSpoofTicks++;
            } else {
                data.groundSpoofTicks = Math.max(0, data.groundSpoofTicks - 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void runChecks(ServerPlayer player, PlayerData data) {
        for (AbstractCheck check : checks) check.check(player, data);
    }

    private PlayerData getOrCreateData(ServerPlayer player) {
        return playerDataMap.computeIfAbsent(player.getUUID(),
                uuid -> new PlayerData(player.getX(), player.getY(), player.getZ()));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public PlayerData      getPlayerData(UUID uuid)     { return playerDataMap.get(uuid); }
    public PlayerSnapshot  getSnapshot(UUID uuid)       { return snapshots.get(uuid); }
    public PhysicsResult   getPhysicsResult(UUID uuid)  { return physicsResults.get(uuid); }
    public PlayerAnalytics getAnalytics(UUID uuid)      { return analytics.get(uuid); }

    public RotationProfile getRotationProfile(UUID uuid) {
        PlayerAnalytics a = analytics.get(uuid); return a != null ? a.rotation : null;
    }
    public TimingProfile getTimingProfile(UUID uuid) {
        PlayerAnalytics a = analytics.get(uuid); return a != null ? a.timing : null;
    }
    public MovementProfile getMovementProfile(UUID uuid) {
        PlayerAnalytics a = analytics.get(uuid); return a != null ? a.movement : null;
    }
    public PlayerBaseline getPlayerBaseline(UUID uuid) {
        PlayerAnalytics a = analytics.get(uuid); return a != null ? a.baseline : null;
    }

    public List<AbstractCheck>       getChecks()  { return checks; }
    public Map<UUID, PlayerData>     getAllData()  { return playerDataMap; }

    // -------------------------------------------------------------------------
    // Event-driven check accessors — used by mixins instead of stream filtering
    // -------------------------------------------------------------------------

    public BadPacketsCheck   getBadPacketsCheck()   { return badPacketsCheck; }
    public TimerCheck        getTimerCheck()        { return timerCheck; }
    public TeleportCheck     getTeleportCheck()     { return teleportCheck; }
    public FastBreakCheck    getFastBreakCheck()     { return fastBreakCheck; }
    public CriticalsCheck    getCriticalsCheck()    { return criticalsCheck; }
    public ReachCheck        getReachCheck()        { return reachCheck; }
    public KillAuraCheck     getKillAuraCheck()     { return killAuraCheck; }
    public AutoClickerCheck  getAutoClickerCheck()  { return autoClickerCheck; }
    public InventoryCheck    getInventoryCheck()    { return inventoryCheck; }

    // -------------------------------------------------------------------------
    // Performance monitoring accessors
    // -------------------------------------------------------------------------

    /** Current server tick duration in ms (smoothed). 50.0 = 20 TPS. */
    public static double getCurrentMspt() { return currentMspt; }

    /** Current estimated TPS. */
    public static double getCurrentTps() {
        return currentMspt > 0 ? Math.min(20.0, 1000.0 / currentMspt) : 20.0;
    }

    /** CheckManager processing time for the most recent tick in nanoseconds. */
    public static long getLastTickNanos() { return lastTickNanos; }

    /** Exponentially weighted average of CheckManager processing time in nanos. */
    public static double getAvgTickNanos() { return avgTickNanos; }
}

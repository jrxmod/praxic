package com.jrxmod.praxic.manager;

import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.checks.*;
import com.jrxmod.praxic.data.MovementState;
import com.jrxmod.praxic.data.PlayerData;
import com.jrxmod.praxic.engine.analysis.RotationAnalyzer;
import com.jrxmod.praxic.engine.analysis.RotationProfile;
import com.jrxmod.praxic.engine.analysis.TimingAnalyzer;
import com.jrxmod.praxic.engine.analysis.TimingProfile;
import com.jrxmod.praxic.engine.data.PlayerSnapshot;
import com.jrxmod.praxic.engine.data.SnapshotBuilder;
import com.jrxmod.praxic.engine.physics.PhysicsEngine;
import com.jrxmod.praxic.engine.physics.PhysicsResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class CheckManager {

    private final List<AbstractCheck>          checks          = new ArrayList<>();
    private final Map<UUID, PlayerData>        playerDataMap   = new HashMap<>();
    private final Map<UUID, PlayerSnapshot>    snapshots       = new HashMap<>();
    private final Map<UUID, PhysicsResult>     physicsResults  = new HashMap<>();
    private final Map<UUID, RotationProfile>   rotationProfiles = new HashMap<>();
    private final Map<UUID, TimingProfile>     timingProfiles  = new HashMap<>();

    private final PhysicsEngine    physicsEngine    = new PhysicsEngine();
    private final RotationAnalyzer rotationAnalyzer = new RotationAnalyzer();
    private final TimingAnalyzer   timingAnalyzer   = new TimingAnalyzer();

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
        checks.add(new RotationCheck());
        checks.add(new SprintCheck());
        checks.add(new BoatFlyCheck());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            decayTickCounter++;
            boolean doDecay = decayTickCounter >= DECAY_INTERVAL_TICKS;
            if (doDecay) decayTickCounter = 0;

            List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            for (ServerPlayer player : players) {
                PlayerData data = getOrCreateData(player);
                UUID uuid = player.getUUID();

                // 1. Decay VL before checks to keep thresholds fair
                if (doDecay) {
                    data.decayViolations(DECAY_INTERVAL_MS);
                }

                // 2. Skip dead players entirely — death screen causes false positives
                //    health <= 0 is more reliable than isDeadOrDying() which can return
                //    false after the death animation completes but before respawn
                if (player.getHealth() <= 0) {
                    data.yPredictionActive     = false;
                    data.yPredictionGraceTicks = 0;
                    data.airTicks              = 0;
                    data.boatAirTicks          = 0;
                    physicsEngine.reset(uuid);
                    rotationAnalyzer.reset(uuid);
                    timingAnalyzer.reset(uuid);
                    data.updatePosition(player.getX(), player.getY(), player.getZ());
                    continue;
                }

                // 3. Compute movement state — single source of truth for all checks
                updateMovementState(player, data);

                // 4. Sync derived legacy fields from the state machine
                syncDerivedFields(player, data);

                // 5. Build immutable snapshot — engine layers read from this
                PlayerSnapshot snapshot = SnapshotBuilder.build(player, data);
                snapshots.put(uuid, snapshot);

                // 6. Run physics simulation
                PhysicsResult physics = physicsEngine.simulate(uuid, snapshot, data);
                physicsResults.put(uuid, physics);

                // 7. Run analysis layer
                RotationProfile rotProfile = rotationAnalyzer.analyse(uuid, snapshot);
                rotationProfiles.put(uuid, rotProfile);

                TimingProfile timProfile = timingAnalyzer.analyse(uuid, data);
                timingProfiles.put(uuid, timProfile);

                // 8. Run checks (whitelisted players are skipped entirely)
                if (!Praxic.getWhitelistManager().isWhitelisted(uuid)) {
                    runChecks(player, data);
                }

                // 9. Update safe position when firmly on the ground
                if (player.onGround() && !player.isDeadOrDying()) {
                    data.lastSafeX = player.getX();
                    data.lastSafeY = player.getY();
                    data.lastSafeZ = player.getZ();
                }

                // 10. Snapshot position and rotation for next tick
                data.updatePosition(player.getX(), player.getY(), player.getZ());
                data.lastYaw   = player.getYRot();
                data.lastPitch = player.getXRot();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            playerDataMap.put(player.getUUID(),
                    new PlayerData(player.getX(), player.getY(), player.getZ()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            playerDataMap.remove(uuid);
            snapshots.remove(uuid);
            physicsResults.remove(uuid);
            physicsEngine.reset(uuid);
            rotationProfiles.remove(uuid);
            timingProfiles.remove(uuid);
            rotationAnalyzer.reset(uuid);
            timingAnalyzer.reset(uuid);
        });
    }

    // -------------------------------------------------------------------------
    // Movement State Machine
    // -------------------------------------------------------------------------

    /**
     * Determines the player's movement state this tick and stores it.
     * Called once per tick, before any check runs.
     * Priority: WATER > CLIMB > GROUND > JUMP > FALLING > AIR
     */
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

    /**
     * Keeps legacy boolean/counter fields in sync with the state machine.
     * Existing checks read these fields without any modification.
     */
    private void syncDerivedFields(ServerPlayer player, PlayerData data) {
        MovementState prev = data.prevMovementState;
        MovementState curr = data.movementState;

        // Decrement join grace each tick until expired
        if (data.joinGraceTicks > 0) data.joinGraceTicks--;

        data.wasOnGround = (prev == MovementState.GROUND);
        data.wasInWater  = (prev == MovementState.WATER);

        boolean airborne = curr == MovementState.JUMP
                        || curr == MovementState.AIR
                        || curr == MovementState.FALLING;
        if (airborne) {
            data.airTicks++;
        } else {
            data.airTicks = 0;
        }

        boolean justLeftWater = (prev == MovementState.WATER) && (curr != MovementState.WATER);
        if (justLeftWater) {
            data.waterExitTicks       = WATER_EXIT_GRACE_TICKS;
            data.jesusWaterGraceTicks = WATER_EXIT_GRACE_TICKS;
        } else {
            if (data.waterExitTicks       > 0) data.waterExitTicks--;
            if (data.jesusWaterGraceTicks > 0) data.jesusWaterGraceTicks--;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void runChecks(ServerPlayer player, PlayerData data) {
        for (AbstractCheck check : checks) {
            check.check(player, data);
        }
    }

    private PlayerData getOrCreateData(ServerPlayer player) {
        return playerDataMap.computeIfAbsent(player.getUUID(),
                uuid -> new PlayerData(player.getX(), player.getY(), player.getZ()));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    /** Returns the latest snapshot for the given player, or null if not yet built. */
    public PlayerSnapshot getSnapshot(UUID uuid) {
        return snapshots.get(uuid);
    }

    /** Returns the latest physics result for the given player, or null. */
    public PhysicsResult getPhysicsResult(UUID uuid) {
        return physicsResults.get(uuid);
    }

    /** Returns the latest rotation profile for the given player, or null. */
    public RotationProfile getRotationProfile(UUID uuid) {
        return rotationProfiles.get(uuid);
    }

    /** Returns the latest timing profile for the given player, or null. */
    public TimingProfile getTimingProfile(UUID uuid) {
        return timingProfiles.get(uuid);
    }

    public List<AbstractCheck> getChecks() {
        return checks;
    }

    public Map<UUID, PlayerData> getAllData() {
        return playerDataMap;
    }
}

package com.jrxmod.praxic.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records per-tick snapshots of a player's state for manual inspection.
 * Activated by /praxic debug <player>. Records for 600 ticks (30 seconds at 20 TPS)
 * then writes a JSON file to config/praxic-debug-<name>-<timestamp>.json.
 */
public class DebugRecorder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int RECORD_TICKS = 600;
    private static final Path DEBUG_DIR = Paths.get("config", "praxic-debug");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private static final Map<UUID, Recording> active = new ConcurrentHashMap<>();

    private static class TickEntry {
        public int tick;
        public double x, y, z;
        public float yaw, pitch;
        public String state;
        public int airTicks;
        public double confidence;
        public double anomaly;
        public double mspt;
        public int ping;
        public boolean onGround;
        public boolean inWater;
        public Map<String, Integer> violations;
    }

    private static class Recording {
        final UUID uuid;
        final String playerName;
        final long startTime;
        final List<TickEntry> ticks = new ArrayList<>();
        int elapsed = 0;

        Recording(UUID uuid, String name) {
            this.uuid = uuid;
            this.playerName = name;
            this.startTime = System.currentTimeMillis();
        }
    }

    /** Starts recording a player. Returns false if already recording. */
    public static boolean start(UUID uuid, String playerName) {
        if (active.containsKey(uuid)) return false;
        active.put(uuid, new Recording(uuid, playerName));
        Praxic.LOGGER.info("[PRAXIC] Debug recording started for {} ({} ticks).", playerName, RECORD_TICKS);
        return true;
    }

    /** Returns true if a recording is active for the player. */
    public static boolean isRecording(UUID uuid) {
        return active.containsKey(uuid);
    }

    /** Called every tick from CheckManager — captures data for active recordings. */
    public static void tick(net.minecraft.server.level.ServerPlayer player) {
        Recording rec = active.get(player.getUUID());
        if (rec == null) return;

        PlayerData data = Praxic.getCheckManager().getPlayerData(player.getUUID());
        TickEntry entry = new TickEntry();
        entry.tick = rec.elapsed;
        entry.x = round2(player.getX());
        entry.y = round2(player.getY());
        entry.z = round2(player.getZ());
        entry.yaw = Math.round(player.getYRot() * 100.0f) / 100.0f;
        entry.pitch = Math.round(player.getXRot() * 100.0f) / 100.0f;
        entry.state = data != null ? data.movementState.name() : "UNKNOWN";
        entry.airTicks = data != null ? data.airTicks : 0;
        entry.confidence = round3(Praxic.getConfidenceEngine().getScore(player.getUUID()));
        entry.anomaly = round3(Praxic.getAnomalyScoreEngine().getScore(player.getUUID()));
        entry.mspt = Math.round(CheckManager.getCurrentMspt() * 100.0) / 100.0;
        entry.ping = player.connection.latency();
        entry.onGround = player.onGround();
        entry.inWater = player.isInWater();
        entry.violations = data != null ? new HashMap<>(data.violations) : Collections.emptyMap();

        rec.ticks.add(entry);
        rec.elapsed++;

        if (rec.elapsed >= RECORD_TICKS) {
            save(rec);
            active.remove(player.getUUID());
        }
    }

    private static void save(Recording rec) {
        try {
            Files.createDirectories(DEBUG_DIR);
            String timestamp = FORMATTER.format(Instant.now());
            String filename = "debug-" + rec.playerName + "-" + timestamp + ".json";
            Path path = DEBUG_DIR.resolve(filename);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("player", rec.playerName);
            output.put("uuid", rec.uuid.toString());
            output.put("startTime", Instant.ofEpochMilli(rec.startTime).toString());
            output.put("endTime", Instant.now().toString());
            output.put("ticks", rec.ticks.size());
            output.put("recording", rec.ticks);

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(output, writer);
            }
            Praxic.LOGGER.info("[PRAXIC] Debug recording saved: {} ({} ticks).", filename, rec.ticks.size());
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save debug recording.", e);
        }
    }

    /** Returns ticks remaining for an active recording, or -1 if not recording. */
    public static int ticksRemaining(UUID uuid) {
        Recording rec = active.get(uuid);
        if (rec == null) return -1;
        return Math.max(0, RECORD_TICKS - rec.elapsed);
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}

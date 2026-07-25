package com.jrxmod.praxic.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jrxmod.praxic.Praxic;
import com.jrxmod.praxic.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores compact evidence packets for each violation.
 *
 * HistoryManager is human-readable chronology. EvidenceManager is richer and
 * records the state that staff/API/dashboard need for review: confidence,
 * anomaly, ping, location, movement state and the exact action chosen.
 */
public class EvidenceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path EVIDENCE_PATH = Paths.get("config", "praxic-evidence.json");
    private static final int MAX_GLOBAL_ENTRIES = 500;
    private static final int MAX_ENTRIES_PER_PLAYER = 80;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final List<EvidenceEntry> entries = new ArrayList<>();

    public EvidenceManager() {
        load();
    }

    public synchronized void record(
            ServerPlayer player,
            PlayerData data,
            String checkName,
            int violations,
            String details,
            String action,
            double confidence,
            double anomaly
    ) {
        EvidenceEntry e = new EvidenceEntry();
        e.timestamp = FORMATTER.format(Instant.now());
        e.uuid = player.getUUID().toString();
        e.playerName = player.getName().getString();
        e.check = checkName;
        e.vl = violations;
        e.details = details;
        e.action = action;
        e.confidence = round3(confidence);
        e.anomaly = round3(anomaly);
        e.ping = player.connection.latency();
        e.world = player.serverLevel().dimension().location().toString();
        e.x = round2(player.getX());
        e.y = round2(player.getY());
        e.z = round2(player.getZ());
        e.movementState = data.movementState.name();
        e.airTicks = data.airTicks;
        e.ghostTraps = Praxic.getGhostEntityManager() != null
                ? Praxic.getGhostEntityManager().getActiveGhostCount(player.getUUID()) : 0;

        entries.add(e);
        trim();
        save();
    }

    public synchronized List<EvidenceEntry> getRecent(int limit) {
        int safeLimit = Math.max(0, limit);
        List<EvidenceEntry> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && result.size() < safeLimit; i--) {
            result.add(entries.get(i));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<EvidenceEntry> getRecent(UUID uuid, int limit) {
        int safeLimit = Math.max(0, limit);
        String key = uuid.toString();
        List<EvidenceEntry> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && result.size() < safeLimit; i--) {
            EvidenceEntry e = entries.get(i);
            if (key.equals(e.uuid)) result.add(e);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int count() {
        return entries.size();
    }

    public synchronized void clear(UUID uuid) {
        String key = uuid.toString();
        entries.removeIf(e -> key.equals(e.uuid));
        save();
    }

    private void trim() {
        while (entries.size() > MAX_GLOBAL_ENTRIES) {
            entries.remove(0);
        }

        // Keep a per-player cap too, so one noisy player cannot consume the log.
        Map<String, Integer> seenByPlayer = new HashMap<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            EvidenceEntry e = entries.get(i);
            String key = e.uuid != null ? e.uuid : "";
            int seen = seenByPlayer.getOrDefault(key, 0) + 1;
            if (seen > MAX_ENTRIES_PER_PLAYER) {
                entries.remove(i);
            } else {
                seenByPlayer.put(key, seen);
            }
        }
    }

    private void load() {
        try {
            Files.createDirectories(EVIDENCE_PATH.getParent());
            if (!Files.exists(EVIDENCE_PATH)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(EVIDENCE_PATH)) {
                Type type = new TypeToken<List<EvidenceEntry>>() {}.getType();
                List<EvidenceEntry> loaded = GSON.fromJson(reader, type);
                if (loaded != null) entries.addAll(loaded);
            }
            trim();
            Praxic.LOGGER.info("[PRAXIC] Evidence loaded ({} entries).", entries.size());
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to load evidence.", e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(EVIDENCE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(EVIDENCE_PATH)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save evidence.", e);
        }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    public static class EvidenceEntry {
        public String timestamp;
        public String uuid;
        public String playerName;
        public String check;
        public int vl;
        public String details;
        public String action;
        public double confidence;
        public double anomaly;
        public int ping;
        public String world;
        public double x;
        public double y;
        public double z;
        public String movementState;
        public int airTicks;
        public int ghostTraps;
    }
}

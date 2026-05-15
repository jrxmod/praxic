package com.jrxmod.praxic.manager;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path HISTORY_PATH = Paths.get("config", "praxic-history.json");
    private static final int MAX_ENTRIES_PER_PLAYER = 50;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // UUID string -> list of violation entries
    private final Map<String, List<ViolationEntry>> history = new HashMap<>();

    public HistoryManager() {
        load();
    }

    public void record(UUID uuid, String checkName, int vl, String details, String action) {
        String key = uuid.toString();
        history.computeIfAbsent(key, k -> new ArrayList<>());
        List<ViolationEntry> entries = history.get(key);

        entries.add(new ViolationEntry(
                FORMATTER.format(Instant.now()),
                checkName,
                vl,
                details,
                action
        ));

        // Keep only the latest MAX_ENTRIES_PER_PLAYER entries
        if (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.subList(0, entries.size() - MAX_ENTRIES_PER_PLAYER).clear();
        }

        save();
    }

    public List<ViolationEntry> getHistory(UUID uuid) {
        return Collections.unmodifiableList(
                history.getOrDefault(uuid.toString(), Collections.emptyList())
        );
    }

    public void clearHistory(UUID uuid) {
        history.remove(uuid.toString());
        save();
    }

    private void load() {
        try {
            Files.createDirectories(HISTORY_PATH.getParent());
            if (!Files.exists(HISTORY_PATH)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(HISTORY_PATH)) {
                Type type = new TypeToken<Map<String, List<ViolationEntry>>>() {}.getType();
                Map<String, List<ViolationEntry>> loaded = GSON.fromJson(reader, type);
                if (loaded != null) history.putAll(loaded);
            }
            Praxic.LOGGER.info("[PRAXIC] History loaded ({} players).", history.size());
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to load history.", e);
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(HISTORY_PATH)) {
            GSON.toJson(history, writer);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save history.", e);
        }
    }

    // Single violation event record
    public static class ViolationEntry {
        public final String timestamp;
        public final String check;
        public final int vl;
        public final String details;
        public final String action;

        public ViolationEntry(String timestamp, String check, int vl, String details, String action) {
            this.timestamp = timestamp;
            this.check = check;
            this.vl = vl;
            this.details = details;
            this.action = action;
        }
    }
}

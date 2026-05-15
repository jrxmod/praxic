package com.jrxmod.praxic.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class WhitelistManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path WHITELIST_PATH = Paths.get("config", "praxic-whitelist.json");

    // UUID set of whitelisted players
    private final Set<UUID> whitelisted = new HashSet<>();

    public WhitelistManager() {
        load();
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelisted.contains(uuid);
    }

    public boolean add(UUID uuid) {
        boolean added = whitelisted.add(uuid);
        if (added) save();
        return added;
    }

    public boolean remove(UUID uuid) {
        boolean removed = whitelisted.remove(uuid);
        if (removed) save();
        return removed;
    }

    public Set<UUID> getAll() {
        return Collections.unmodifiableSet(whitelisted);
    }

    private void load() {
        try {
            Files.createDirectories(WHITELIST_PATH.getParent());
            if (!Files.exists(WHITELIST_PATH)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(WHITELIST_PATH)) {
                Type type = new TypeToken<Set<String>>() {}.getType();
                Set<String> raw = GSON.fromJson(reader, type);
                if (raw != null) {
                    for (String s : raw) {
                        try {
                            whitelisted.add(UUID.fromString(s));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            Praxic.LOGGER.info("[PRAXIC] Whitelist loaded ({} entries).", whitelisted.size());
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to load whitelist.", e);
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(WHITELIST_PATH)) {
            // Store as list of UUID strings for readability
            Set<String> raw = new HashSet<>();
            for (UUID uuid : whitelisted) raw.add(uuid.toString());
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save whitelist.", e);
        }
    }
}

package com.jrxmod.praxic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.nio.file.*;

public class PraxicConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "praxic.json");

    // --- FlyCheck ---
    public boolean flyCheckEnabled = true;
    public int flyMaxAirTicks = 80;        // Сколько тиков в воздухе до флага (80 = ~4 секунды)
    public int flyMaxViolations = 5;       // Нарушений до действия
    public String flyAction = "kick";      // warn | kick

    // --- SpeedCheck ---
    public boolean speedCheckEnabled = true;
    public double speedMaxBlocksPerTick = 0.7; // Максимум блоков в тик (норма ~0.29 спринт)
    public int speedMaxViolations = 8;
    public String speedAction = "warn";    // warn | kick

    // --- Общее ---
    public boolean enableLogging = true;   // Логировать нарушения в консоль

    public static PraxicConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    PraxicConfig config = GSON.fromJson(reader, PraxicConfig.class);
                    Praxic.LOGGER.info("[PRAXIC] Config loaded.");
                    return config;
                }
            } else {
                PraxicConfig config = new PraxicConfig();
                config.save();
                Praxic.LOGGER.info("[PRAXIC] Default config created.");
                return config;
            }
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to load config, using defaults.", e);
            return new PraxicConfig();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save config.", e);
        }
    }
}

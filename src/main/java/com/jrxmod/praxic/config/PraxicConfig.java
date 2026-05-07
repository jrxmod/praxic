package com.jrxmod.praxic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.nio.file.*;

public class PraxicConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "praxic.json");

    // FlyCheck settings
    public boolean flyCheckEnabled = true;
    public int flyMaxAirTicks = 80;
    public int flyMaxViolations = 5;
    public String flyAction = "kick";

    // SpeedCheck settings
    public boolean speedCheckEnabled = true;
    public double speedMaxBlocksPerTick = 1.3;
    public int speedMaxViolations = 8;
    public String speedAction = "warn";

    // NoFallCheck settings
    public boolean noFallCheckEnabled = true;
    public int noFallMaxViolations = 3;
    public String noFallAction = "kick";

    // ReachCheck settings
    public boolean reachCheckEnabled = true;
    public int reachMaxViolations = 5;
    public String reachAction = "kick";

    // KillAuraCheck settings
    public boolean killAuraCheckEnabled = true;
    public int killAuraCheckMaxViolations = 5;
    public String killAuraCheckAction = "kick";

    // ScaffoldCheck settings
    public boolean scaffoldCheckEnabled = true;
    public int scaffoldMaxBlocksPerSecond = 8;
    public int scaffoldMaxViolations = 5;
    public String scaffoldAction = "kick";

    // AutoTotemCheck settings
    public boolean autoTotemCheckEnabled = true;
    public int autoTotemMaxViolations = 3;
    public String autoTotemAction = "kick";

    // InventoryCheck settings
    public boolean inventoryCheckEnabled = true;
    public int inventoryMaxClicksPerSecond = 20;
    public int inventoryMaxViolations = 5;
    public String inventoryAction = "kick";

    // General settings
    public boolean enableLogging = true;
    public boolean enableStaffAlerts = true;

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

package com.jrxmod.praxic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.nio.file.*;

public class PraxicConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "praxic.json");

    /**
     * Current configuration schema version. Incremented when a release adds,
     * removes, or renames fields, and matched by stepwise blocks in migrate().
     */
    public static final int CURRENT_CONFIG_VERSION = 3;

    public int configVersion = CURRENT_CONFIG_VERSION;

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

    // PhaseCheck settings
    public boolean phaseCheckEnabled = true;
    public double phaseMinHorizontalMove = 0.03;
    public int phaseMaxTicksInBlock = 8;
    public int phaseMaxViolations = 5;
    public String phaseAction = "setback";

    // NoSlowCheck settings
    // 0.30 covers vanilla sprinting while eating (0.286 b/t) plus lag margin.
    public boolean noSlowCheckEnabled = true;
    public double noSlowMaxBlocksPerTick = 0.30;
    public int noSlowMaxViolations = 5;
    public String noSlowAction = "warn";

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

    // GhostTrapCheck settings (invisible honeypot entities)
    public boolean ghostTrapCheckEnabled = true;
    public int ghostTrapMaxViolations = 1;
    public String ghostTrapAction = "kick";
    public long ghostTrapLifetimeMs = 25_000L;
    public long ghostTrapSpawnCooldownMs = 40_000L;
    public double ghostTrapSpawnChance = 0.07;

    // CriticalsCheck settings
    public boolean criticalsCheckEnabled = true;
    public int criticalsMaxViolations = 5;
    public String criticalsAction = "warn";

    // ScaffoldCheck settings
    // Vanilla ceiling is 1 placement per game tick (20 blocks/sec); 12 covers
    // fast legitimate bridging while still catching automated bridging.
    public boolean scaffoldCheckEnabled = true;
    public int scaffoldMaxBlocksPerSecond = 12;
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

    // AutoClickerCheck settings
    public boolean autoClickerCheckEnabled = true;
    public int autoClickerMaxCps = 20;
    public int autoClickerMaxViolations = 5;
    public String autoClickerAction = "kick";

    // TimerCheck settings
    public boolean timerCheckEnabled = true;
    public int timerMaxPacketsPerSecond = 24;
    public int timerMaxViolations = 5;
    public String timerAction = "kick";

    // BadPacketsCheck settings
    public boolean badPacketsCheckEnabled = true;
    public int badPacketsBufferThreshold = 2;
    public int badPacketsMaxViolations = 3;
    public String badPacketsAction = "kick";

    // FastBreakCheck settings
    public boolean fastBreakCheckEnabled = true;
    public double fastBreakSpeedMultiplier = 0.4;
    public int fastBreakMaxViolations = 5;
    public String fastBreakAction = "kick";

    // JesusCheck settings
    public boolean jesusCheckEnabled = true;
    public int jesusMaxViolations = 5;
    public String jesusAction = "kick";

    // VelocityCheck settings
    public boolean velocityCheckEnabled = true;
    public int velocityMaxViolations = 5;
    public String velocityAction = "kick";

    // YPredictionCheck settings
    public boolean yPredictionCheckEnabled = true;
    public int yPredictionMaxViolations = 5;
    public String yPredictionAction = "setback";

    // RotationCheck settings
    public boolean rotationCheckEnabled = true;
    public int rotationMaxViolations = 8;
    public String rotationAction = "warn";

    // SprintCheck settings
    public boolean sprintCheckEnabled = true;
    public int sprintMaxViolations = 5;
    public String sprintAction = "warn";

    // BoatFlyCheck settings
    public boolean boatFlyCheckEnabled = true;
    public int boatFlyMaxViolations = 5;
    public String boatFlyAction = "kick";

    // PostKillSnapCheck settings
    public boolean postKillSnapCheckEnabled = true;
    public double postKillSnapMaxAngle = 90.0;
    public int postKillSnapMaxViolations = 5;
    public String postKillSnapAction = "warn";

    // ElytraFlyCheck settings
    public boolean elytraFlyCheckEnabled = true;
    public int elytraFlyMaxViolations = 5;
    public String elytraFlyAction = "kick";

    // StepCheck settings
    public boolean stepCheckEnabled = true;
    public double stepMaxHeight = 0.75;
    public int stepMaxViolations = 5;
    public String stepAction = "setback";

    // TowerCheck settings
    public boolean towerCheckEnabled = true;
    public int towerMaxBlocksPerSecond = 6;
    public int towerMaxViolations = 5;
    public String towerAction = "warn";

    // GroundSpoofCheck settings
    public boolean groundSpoofCheckEnabled = true;
    public int groundSpoofMaxViolations = 5;
    public String groundSpoofAction = "kick";

    // FastPlaceCheck settings
    // Vanilla ceiling is 1 placement per game tick (20 blocks/sec).
    public boolean fastPlaceCheckEnabled = true;
    public int fastPlaceMaxBlocksPerSecond = 20;
    public int fastPlaceMaxViolations = 5;
    public String fastPlaceAction = "warn";

    // TeleportCheck settings
    // A single move-packet jump above this distance (blocks) with no recent
    // server-initiated teleport is a Blink / Teleport signature. Legitimate
    // teleports are exempt via teleport confirmations.
    public boolean teleportCheckEnabled = true;
    public double teleportMaxBlocksPerTick = 6.0;
    public int teleportMaxViolations = 3;
    public String teleportAction = "warn";

    // UpdateChecker settings
    public boolean enableUpdateChecker = true;

    // Discord Webhook settings
    public boolean enableDiscordWebhook = false;
    public String discordWebhookUrl = "YOUR_WEBHOOK_URL_HERE";

    // General settings
    public boolean enableLogging = true;
    public boolean enableStaffAlerts = true;

    // Alert rate limiting (per player + check). Set to 0 to disable throttling.
    public long staffAlertCooldownMs = 1000L;
    public long discordAlertCooldownMs = 2000L;

    // Confidence action policy
    public double confidenceWarnThreshold = 0.30;
    public double confidenceSetbackThreshold = 0.60;
    public double confidenceKickThreshold = 0.80;
    public double confidenceBanThreshold = 0.95;
    public boolean confidenceAutoBan = true;

    // Freeze punishment — duration in ticks the player is held in place.
    public int freezeDurationTicks = 60;

    // Web Dashboard settings
    public boolean enableWebDashboard = true;
    public int webDashboardPort = 8765;

    /**
     * Optional access token for the web dashboard.
     * If non-empty, all requests must include header "X-Praxic-Token: <token>"
     * or query param "?token=<token>".
     * Leave empty to disable auth (safe since dashboard is 127.0.0.1 only).
     */
    public String webDashboardToken = "";

    public static PraxicConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            PraxicConfig config;
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, PraxicConfig.class);
                }
                if (config == null) config = new PraxicConfig();
                config.migrate();
            } else {
                config = new PraxicConfig();
            }
            // Rewriting merges any newly added fields into the file.
            config.save();
            Praxic.LOGGER.info("[PRAXIC] Config loaded (schema v{}).", config.configVersion);
            return config;
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to load config, using defaults.", e);
            return new PraxicConfig();
        }
    }

    /**
     * Brings a loaded config up to the current schema version.
     * Absent fields already fall back to their field initialisers through Gson,
     * so migrations only need to handle renames, type changes, and value
     * normalisation. Each block must be idempotent because the file is
     * rewritten on every load.
     */
    private void migrate() {
        // No migrations are required below CURRENT_CONFIG_VERSION yet.
        // Future example, from v4 to v5:
        // if (configVersion < 5) { ...normalise...; configVersion = 5; }
        configVersion = CURRENT_CONFIG_VERSION;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save config.", e);
        }
    }
}

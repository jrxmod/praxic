package com.jrxmod.praxic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jrxmod.praxic.Praxic;

import java.io.*;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

public class PraxicConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "praxic.json");

    /**
     * Current configuration schema version. Incremented when a release adds,
     * removes, or renames fields, and matched by stepwise blocks in migrate().
     */
    public static final int CURRENT_CONFIG_VERSION = 11;

    public int configVersion = CURRENT_CONFIG_VERSION;

    // FlyCheck settings
    public boolean flyCheckEnabled = true;
    public int flyMaxAirTicks = 80;
    public String flyAction = "kick";

    // SpeedCheck settings
    public boolean speedCheckEnabled = true;
    public double speedMaxBlocksPerTick = 1.3;
    public String speedAction = "warn";

    // PhaseCheck settings
    public boolean phaseCheckEnabled = true;
    public double phaseMinHorizontalMove = 0.03;
    public int phaseMaxTicksInBlock = 8;
    public String phaseAction = "setback";

    // NoSlowCheck settings
    // Vanilla walk is ~0.215 b/t; sprinting while using an item (cheat) is
    // ~0.286. The cap sits between them: the first-use grace covers the
    // sprint-to-walk transition ticks.
    public boolean noSlowCheckEnabled = true;
    public double noSlowMaxBlocksPerTick = 0.24;
    public String noSlowAction = "warn";

    // NoFallCheck settings
    public boolean noFallCheckEnabled = true;
    public String noFallAction = "kick";

    // ReachCheck settings
    public boolean reachCheckEnabled = true;
    public String reachAction = "kick";

    // KillAuraCheck settings
    public boolean killAuraCheckEnabled = true;
    public String killAuraCheckAction = "kick";

    // GhostTrapCheck settings (invisible honeypot entities)
    public boolean ghostTrapCheckEnabled = true;
    public String ghostTrapAction = "kick";
    public long ghostTrapLifetimeMs = 25_000L;
    public long ghostTrapSpawnCooldownMs = 40_000L;
    public double ghostTrapSpawnChance = 0.07;
    /** Minimum confidence score before a honeypot is spawned for a player. */
    public double ghostTrapConfidenceThreshold = 0.40;

    // CriticalsCheck settings
    public boolean criticalsCheckEnabled = true;
    public String criticalsAction = "warn";

    // ScaffoldCheck settings
    // Vanilla ceiling is 1 placement per game tick (20 blocks/sec); 12 covers
    // fast legitimate bridging while still catching automated bridging.
    public boolean scaffoldCheckEnabled = true;
    public int scaffoldMaxBlocksPerSecond = 12;
    public String scaffoldAction = "kick";

    // AutoTotemCheck settings
    public boolean autoTotemCheckEnabled = true;
    public String autoTotemAction = "kick";

    // InventoryCheck settings
    public boolean inventoryCheckEnabled = true;
    public int inventoryMaxClicksPerSecond = 20;
    public String inventoryAction = "kick";

    // AutoClickerCheck settings
    public boolean autoClickerCheckEnabled = true;
    public int autoClickerMaxCps = 20;
    public String autoClickerAction = "kick";

    // TimerCheck settings
    // Vanilla moving is ~20 pos packets/s. Timer x2.0 is ~40. 32 catches x2
    // after ~2s. Values >= 50 are the stale 55 default and miss x2.0.
    public boolean timerCheckEnabled = true;
    public int timerMaxPacketsPerSecond = 32;
    public String timerAction = "kick";

    // BadPacketsCheck settings
    public boolean badPacketsCheckEnabled = true;
    public int badPacketsBufferThreshold = 2;
    public String badPacketsAction = "kick";

    // FastBreakCheck settings
    public boolean fastBreakCheckEnabled = true;
    public double fastBreakSpeedMultiplier = 0.4;
    public String fastBreakAction = "kick";

    // JesusCheck settings
    public boolean jesusCheckEnabled = true;
    public String jesusAction = "kick";

    // VelocityCheck settings
    public boolean velocityCheckEnabled = true;
    public String velocityAction = "kick";

    // YPredictionCheck settings
    public boolean yPredictionCheckEnabled = true;
    public String yPredictionAction = "setback";

    // RotationCheck settings
    public boolean rotationCheckEnabled = true;
    public String rotationAction = "warn";

    // SprintCheck settings
    public boolean sprintCheckEnabled = true;
    public String sprintAction = "warn";

    // BoatFlyCheck settings
    public boolean boatFlyCheckEnabled = true;
    public String boatFlyAction = "kick";

    // PostKillSnapCheck settings
    public boolean postKillSnapCheckEnabled = true;
    public double postKillSnapMaxAngle = 90.0;
    public String postKillSnapAction = "warn";

    // ElytraFlyCheck settings
    public boolean elytraFlyCheckEnabled = true;
    public String elytraFlyAction = "kick";

    // StepCheck settings
    public boolean stepCheckEnabled = true;
    public double stepMaxHeight = 0.75;
    public String stepAction = "setback";

    // TowerCheck settings
    public boolean towerCheckEnabled = true;
    public int towerMaxBlocksPerSecond = 10;
    public String towerAction = "warn";

    // GroundSpoofCheck settings
    public boolean groundSpoofCheckEnabled = true;
    public String groundSpoofAction = "kick";

    // FastPlaceCheck settings
    // Vanilla has 4 tick (200ms) rightClickDelay, so max 5 blocks/sec.
    // Removing that delay allows 20/s. 8 is lenient for lag, 12 catches cheat.
    public boolean fastPlaceCheckEnabled = true;
    public int fastPlaceMaxBlocksPerSecond = 12;
    public String fastPlaceAction = "warn";

    // FastLadderCheck settings - vanilla 0.1176 b/t vs modified 0.2872 b/t
    public boolean fastLadderCheckEnabled = true;
    public String fastLadderAction = "warn";

    // TeleportCheck settings
    // A single move-packet jump above this distance (blocks) with no recent
    // server-initiated teleport is a Blink / Teleport signature. Legitimate
    // teleports are exempt via teleport confirmations.
    public boolean teleportCheckEnabled = true;
    public double teleportMaxBlocksPerTick = 6.0;
    public String teleportAction = "warn";

    // AimAssistCheck settings
    public boolean aimAssistCheckEnabled = true;
    public String aimAssistAction = "warn";

    // VehicleFlyCheck settings
    public boolean vehicleFlyCheckEnabled = true;
    public String vehicleFlyAction = "kick";

    // FastUseCheck settings
    public boolean fastUseCheckEnabled = true;
    public String fastUseAction = "warn";

    // AirPlaceCheck settings
    public boolean airPlaceCheckEnabled = true;
    public String airPlaceAction = "warn";

    // MaceSmashCheck settings
    public boolean maceSmashCheckEnabled = true;
    public String maceSmashAction = "kick";

    // WindChargeAbuseCheck settings
    public boolean windChargeAbuseCheckEnabled = true;
    public String windChargeAbuseAction = "warn";

    // AutoArmorCheck settings - automatic armor equipping
    public boolean autoArmorCheckEnabled = true;
    public String autoArmorAction = "kick";

    /**
     * When true, illegal move / attack / air-place / wind-charge packets are
     * cancelled before vanilla applies them. Timer, AutoClicker, Inventory and
     * AimAssist are never cancelled this way.
     */
    public boolean enableMitigation = true;

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

    // Freeze punishment - duration in ticks the player is held in place.
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
                config.validate();
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
        // v3 -> v4: timerMaxPacketsPerSecond was unused and defaulted to 24,
        // which is below vanilla sprint-jump packet rates. Lift the stale default.
        if (configVersion < 4 && timerMaxPacketsPerSecond == 24) {
            timerMaxPacketsPerSecond = 55;
        }
        if (configVersion < 6 && timerMaxPacketsPerSecond == 55) {
            timerMaxPacketsPerSecond = 38;
        }
        // v6 configs kept 55 because schema was already 6 before the 38 migrate.
        if (configVersion < 7 && timerMaxPacketsPerSecond >= 50) {
            timerMaxPacketsPerSecond = 32;
        }
        // v7 -> v8: per-check *MaxViolations fields were removed. They were
        // never read; actions are driven by the confidence engine. Unknown
        // keys in existing files are ignored by Gson, so no code is needed.
        // v8 -> v9: 0.30 sat at the sprint speed and let NoSlowDown pass
        // unflagged; 0.24 sits above the vanilla walk speed still.
        if (configVersion < 9 && noSlowMaxBlocksPerTick >= 0.29) {
            noSlowMaxBlocksPerTick = 0.24;
        }
        // v9 -> v10: fastPlaceMaxBlocksPerSecond was 20 (one per tick) but
        // vanilla placement has 4 tick (200ms) cooldown, so max 5/s. 20 allowed
        // modified clients with removed cooldown to bypass. Lower stale default.
        if (configVersion < 10 && fastPlaceMaxBlocksPerSecond >= 20) {
            fastPlaceMaxBlocksPerSecond = 12;
        }
        // v10 -> v11: towerMaxBlocksPerSecond was 6, too low and caused false
        // positives for legit players holding RMB while jumping. Raise to 10.
        if (configVersion < 11 && towerMaxBlocksPerSecond < 10) {
            towerMaxBlocksPerSecond = 10;
        }
        configVersion = CURRENT_CONFIG_VERSION;
    }

    /**
     * Validates field ranges and logs warnings for out-of-bounds values.
     * Corrected values are clamped rather than rejected, so the server keeps
     * running with sane defaults.
     */
    private void validate() {
        int warnings = 0;
        warnings += clampPositive("flyMaxAirTicks", flyMaxAirTicks, v -> flyMaxAirTicks = v, 1);
        warnings += clampDouble("speedMaxBlocksPerTick", speedMaxBlocksPerTick, v -> speedMaxBlocksPerTick = v, 0.1, 10.0);
        warnings += clampDouble("noSlowMaxBlocksPerTick", noSlowMaxBlocksPerTick, v -> noSlowMaxBlocksPerTick = v, 0.05, 5.0);
        warnings += clampDouble("phaseMinHorizontalMove", phaseMinHorizontalMove, v -> phaseMinHorizontalMove = v, 0.001, 5.0);
        warnings += clampDouble("stepMaxHeight", stepMaxHeight, v -> stepMaxHeight = v, 0.1, 10.0);
        warnings += clampDouble("teleportMaxBlocksPerTick", teleportMaxBlocksPerTick, v -> teleportMaxBlocksPerTick = v, 1.0, 100.0);
        warnings += clampDouble("postKillSnapMaxAngle", postKillSnapMaxAngle, v -> postKillSnapMaxAngle = v, 1.0, 180.0);
        warnings += clampDouble("ghostTrapSpawnChance", ghostTrapSpawnChance, v -> ghostTrapSpawnChance = v, 0.0, 1.0);
        warnings += clampDouble("ghostTrapConfidenceThreshold", ghostTrapConfidenceThreshold, v -> ghostTrapConfidenceThreshold = v, 0.0, 1.0);
        warnings += clampDouble("confidenceWarnThreshold", confidenceWarnThreshold, v -> confidenceWarnThreshold = v, 0.0, 1.0);
        warnings += clampDouble("confidenceSetbackThreshold", confidenceSetbackThreshold, v -> confidenceSetbackThreshold = v, 0.0, 1.0);
        warnings += clampDouble("confidenceKickThreshold", confidenceKickThreshold, v -> confidenceKickThreshold = v, 0.0, 1.0);
        warnings += clampDouble("confidenceBanThreshold", confidenceBanThreshold, v -> confidenceBanThreshold = v, 0.0, 1.0);
        warnings += clampPositive("freezeDurationTicks", freezeDurationTicks, v -> freezeDurationTicks = v, 1);
        warnings += clampInt("webDashboardPort", webDashboardPort, v -> webDashboardPort = v, 1024, 65535);
        warnings += clampDouble("fastBreakSpeedMultiplier", fastBreakSpeedMultiplier, v -> fastBreakSpeedMultiplier = v, 0.05, 2.0);
        warnings += clampInt("timerMaxPacketsPerSecond", timerMaxPacketsPerSecond, v -> timerMaxPacketsPerSecond = v, 30, 200);
        warnings += clampInt("scaffoldMaxBlocksPerSecond", scaffoldMaxBlocksPerSecond, v -> scaffoldMaxBlocksPerSecond = v, 1, 20);
        warnings += clampInt("inventoryMaxClicksPerSecond", inventoryMaxClicksPerSecond, v -> inventoryMaxClicksPerSecond = v, 1, 200);
        warnings += clampInt("autoClickerMaxCps", autoClickerMaxCps, v -> autoClickerMaxCps = v, 1, 200);
        warnings += clampInt("towerMaxBlocksPerSecond", towerMaxBlocksPerSecond, v -> towerMaxBlocksPerSecond = v, 1, 20);
        warnings += clampInt("fastPlaceMaxBlocksPerSecond", fastPlaceMaxBlocksPerSecond, v -> fastPlaceMaxBlocksPerSecond = v, 1, 20);
        warnings += validateActions();
        warnings += validateConfidenceOrder();
        if (warnings > 0) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {} value(s) were out of range and clamped.", warnings);
        }
    }

    private int clampPositive(String name, int val, java.util.function.IntConsumer setter, int min) {
        if (val < min) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} is below minimum {}, clamping.", name, val, min);
            setter.accept(min);
            return 1;
        }
        return 0;
    }

    private int clampDouble(String name, double val, java.util.function.DoubleConsumer setter, double min, double max) {
        if (val < min) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} is below minimum {}, clamping.", name, val, min);
            setter.accept(min);
            return 1;
        }
        if (val > max) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} exceeds maximum {}, clamping.", name, val, max);
            setter.accept(max);
            return 1;
        }
        return 0;
    }

    private int clampInt(String name, int val, java.util.function.IntConsumer setter, int min, int max) {
        if (val < min) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} is below minimum {}, clamping.", name, val, min);
            setter.accept(min);
            return 1;
        }
        if (val > max) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} exceeds maximum {}, clamping.", name, val, max);
            setter.accept(max);
            return 1;
        }
        return 0;
    }

    private int validateActions() {
        int warnings = 0;
        warnings += clampAction("flyAction", flyAction, v -> flyAction = v, "kick");
        warnings += clampAction("speedAction", speedAction, v -> speedAction = v, "warn");
        warnings += clampAction("phaseAction", phaseAction, v -> phaseAction = v, "setback");
        warnings += clampAction("noSlowAction", noSlowAction, v -> noSlowAction = v, "warn");
        warnings += clampAction("noFallAction", noFallAction, v -> noFallAction = v, "kick");
        warnings += clampAction("reachAction", reachAction, v -> reachAction = v, "kick");
        warnings += clampAction("killAuraCheckAction", killAuraCheckAction, v -> killAuraCheckAction = v, "kick");
        warnings += clampAction("ghostTrapAction", ghostTrapAction, v -> ghostTrapAction = v, "kick");
        warnings += clampAction("criticalsAction", criticalsAction, v -> criticalsAction = v, "warn");
        warnings += clampAction("scaffoldAction", scaffoldAction, v -> scaffoldAction = v, "kick");
        warnings += clampAction("autoTotemAction", autoTotemAction, v -> autoTotemAction = v, "kick");
        warnings += clampAction("inventoryAction", inventoryAction, v -> inventoryAction = v, "kick");
        warnings += clampAction("autoClickerAction", autoClickerAction, v -> autoClickerAction = v, "kick");
        warnings += clampAction("timerAction", timerAction, v -> timerAction = v, "kick");
        warnings += clampAction("badPacketsAction", badPacketsAction, v -> badPacketsAction = v, "kick");
        warnings += clampAction("fastBreakAction", fastBreakAction, v -> fastBreakAction = v, "kick");
        warnings += clampAction("jesusAction", jesusAction, v -> jesusAction = v, "kick");
        warnings += clampAction("velocityAction", velocityAction, v -> velocityAction = v, "kick");
        warnings += clampAction("yPredictionAction", yPredictionAction, v -> yPredictionAction = v, "setback");
        warnings += clampAction("rotationAction", rotationAction, v -> rotationAction = v, "warn");
        warnings += clampAction("sprintAction", sprintAction, v -> sprintAction = v, "warn");
        warnings += clampAction("boatFlyAction", boatFlyAction, v -> boatFlyAction = v, "kick");
        warnings += clampAction("postKillSnapAction", postKillSnapAction, v -> postKillSnapAction = v, "warn");
        warnings += clampAction("elytraFlyAction", elytraFlyAction, v -> elytraFlyAction = v, "kick");
        warnings += clampAction("stepAction", stepAction, v -> stepAction = v, "setback");
        warnings += clampAction("towerAction", towerAction, v -> towerAction = v, "warn");
        warnings += clampAction("groundSpoofAction", groundSpoofAction, v -> groundSpoofAction = v, "kick");
        warnings += clampAction("fastPlaceAction", fastPlaceAction, v -> fastPlaceAction = v, "warn");
        warnings += clampAction("teleportAction", teleportAction, v -> teleportAction = v, "warn");
        warnings += clampAction("aimAssistAction", aimAssistAction, v -> aimAssistAction = v, "warn");
        warnings += clampAction("vehicleFlyAction", vehicleFlyAction, v -> vehicleFlyAction = v, "kick");
        warnings += clampAction("fastUseAction", fastUseAction, v -> fastUseAction = v, "warn");
        warnings += clampAction("airPlaceAction", airPlaceAction, v -> airPlaceAction = v, "warn");
        warnings += clampAction("maceSmashAction", maceSmashAction, v -> maceSmashAction = v, "kick");
        warnings += clampAction("windChargeAbuseAction", windChargeAbuseAction, v -> windChargeAbuseAction = v, "warn");
        warnings += clampAction("autoArmorAction", autoArmorAction, v -> autoArmorAction = v, "kick");
        warnings += clampAction("fastLadderAction", fastLadderAction, v -> fastLadderAction = v, "warn");
        return warnings;
    }

    private int clampAction(String name, String value, Consumer<String> setter, String fallback) {
        String normalized = normalizeAction(value);
        if (normalized == null) {
            Praxic.LOGGER.warn("[PRAXIC] Config: {}={} is not a valid action, using {}.", name, value, fallback);
            setter.accept(fallback);
            return 1;
        }
        setter.accept(normalized);
        return 0;
    }

    private static String normalizeAction(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "flag", "warn", "freeze", "setback", "kick", "ban" -> normalized;
            default -> null;
        };
    }

    private int validateConfidenceOrder() {
        if (confidenceWarnThreshold <= confidenceSetbackThreshold
                && confidenceSetbackThreshold <= confidenceKickThreshold
                && confidenceKickThreshold <= confidenceBanThreshold) {
            return 0;
        }
        Praxic.LOGGER.warn("[PRAXIC] Config: confidence thresholds are out of order, using defaults.");
        confidenceWarnThreshold = 0.30;
        confidenceSetbackThreshold = 0.60;
        confidenceKickThreshold = 0.80;
        confidenceBanThreshold = 0.95;
        return 1;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            // Auto backup - preserve the previous config before overwriting
            Path backupPath = CONFIG_PATH.resolveSibling("praxic.json.bak");
            if (Files.exists(CONFIG_PATH) && Files.size(CONFIG_PATH) > 0) {
                Files.copy(CONFIG_PATH, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Praxic.LOGGER.error("[PRAXIC] Failed to save config.", e);
        }
    }
}

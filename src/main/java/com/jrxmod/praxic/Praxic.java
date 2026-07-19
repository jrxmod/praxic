package com.jrxmod.praxic;

import com.jrxmod.praxic.api.PraxicStats;
import com.jrxmod.praxic.commands.PraxicCommand;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.engine.decision.AnomalyScoreEngine;
import com.jrxmod.praxic.engine.decision.ConfidenceEngine;
import com.jrxmod.praxic.engine.trap.GhostEntityManager;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.manager.CheckManager;
import com.jrxmod.praxic.manager.HistoryManager;
import com.jrxmod.praxic.manager.WhitelistManager;
import com.jrxmod.praxic.util.PraxicWebServer;
import com.jrxmod.praxic.util.UpdateChecker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Praxic implements ModInitializer {

    public static final String MOD_ID = "praxic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PraxicConfig       config;
    private static CheckManager       checkManager;
    private static WhitelistManager   whitelistManager;
    private static HistoryManager     historyManager;
    private static ConfidenceEngine   confidenceEngine;
    private static AnomalyScoreEngine anomalyScoreEngine;
    private static PraxicWebServer    webServer;
    private static GhostEntityManager ghostEntityManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[PRAXIC] Initializing AntiCheat...");

        PraxicLogger.init();
        config             = PraxicConfig.load();
        whitelistManager   = new WhitelistManager();
        historyManager     = new HistoryManager();
        confidenceEngine   = new ConfidenceEngine();
        anomalyScoreEngine = new AnomalyScoreEngine();
        ghostEntityManager = new GhostEntityManager();
        Praxic.LOGGER.info("[PRAXIC] GhostEntityManager initialized");
        checkManager       = new CheckManager();
        webServer          = new PraxicWebServer();

        // === РЕГИСТРАЦИЯ КОМАНД СРАЗУ ===
        PraxicCommand.register();
        Praxic.LOGGER.info("[PRAXIC] Commands registration finished");
        UpdateChecker.init();
        PraxicStats.init();

        // Web dashboard
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (config.enableWebDashboard) {
                webServer.start(server, config.webDashboardPort);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> webServer.stop());

        LOGGER.info("[PRAXIC] AntiCheat initialized successfully!");
        PraxicLogger.logInfo("PRAXIC initialized successfully.");
    }

    public static PraxicConfig getConfig() { return config; }

    public static void reloadConfig() {
        config = PraxicConfig.load();
        PraxicLogger.logInfo("Config reloaded.");
    }

    public static CheckManager       getCheckManager()       { return checkManager; }
    public static WhitelistManager   getWhitelistManager()   { return whitelistManager; }
    public static HistoryManager     getHistoryManager()     { return historyManager; }
    public static ConfidenceEngine   getConfidenceEngine()   { return confidenceEngine; }
    public static AnomalyScoreEngine getAnomalyScoreEngine() { return anomalyScoreEngine; }
    public static PraxicWebServer    getWebServer()          { return webServer; }
    public static GhostEntityManager getGhostEntityManager() { return ghostEntityManager; }
}

package com.jrxmod.praxic;

import com.jrxmod.praxic.commands.PraxicCommand;
import com.jrxmod.praxic.config.PraxicConfig;
import com.jrxmod.praxic.logger.PraxicLogger;
import com.jrxmod.praxic.manager.CheckManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Praxic implements ModInitializer {

    public static final String MOD_ID = "praxic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PraxicConfig config;
    private static CheckManager checkManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[PRAXIC] Initializing AntiCheat...");

        PraxicLogger.init();
        config = PraxicConfig.load();
        checkManager = new CheckManager();

        PraxicCommand.register();

        LOGGER.info("[PRAXIC] AntiCheat initialized successfully!");
        PraxicLogger.logInfo("PRAXIC initialized successfully.");
    }

    public static PraxicConfig getConfig() {
        return config;
    }

    // Replaces config instance at runtime — used by /praxic reload
    public static void reloadConfig() {
        config = PraxicConfig.load();
        PraxicLogger.logInfo("Config reloaded.");
    }

    public static CheckManager getCheckManager() {
        return checkManager;
    }
}

package com.jrxmod.praxic;

import com.jrxmod.praxic.commands.PraxicCommand;
import com.jrxmod.praxic.config.PraxicConfig;
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

        config = PraxicConfig.load();
        checkManager = new CheckManager();

        PraxicCommand.register();

        LOGGER.info("[PRAXIC] AntiCheat initialized successfully!");
    }

    public static PraxicConfig getConfig() {
        return config;
    }

    public static CheckManager getCheckManager() {
        return checkManager;
    }
}

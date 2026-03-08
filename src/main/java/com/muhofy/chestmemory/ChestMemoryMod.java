package com.muhofy.chestmemory;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestMemoryMod implements ClientModInitializer {

    public static final String MOD_ID = "chestmemory";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChestMemory] Initializing...");

        // Phase 1'de eklenecek:
        // ChestMemoryConfig.getInstance().load();
        // ChestStorage.getInstance().init();

        LOGGER.info("[ChestMemory] Ready.");
    }
}
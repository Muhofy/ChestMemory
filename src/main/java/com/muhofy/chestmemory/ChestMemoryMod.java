package com.muhofy.chestmemory;

import com.muhofy.chestmemory.config.ChestMemoryConfig;
import com.muhofy.chestmemory.data.ChestStorage;
import com.muhofy.chestmemory.handler.ChestOpenHandler;
import com.muhofy.chestmemory.handler.KeyHandler;
import com.muhofy.chestmemory.handler.WorldEventHandler;
import com.muhofy.chestmemory.ui.ChestMemoryHud;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestMemoryMod implements ClientModInitializer {

    public static final String MOD_ID = "chestmemory";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChestMemory] Initializing...");

        ChestMemoryConfig.getInstance().load();
        ChestStorage.getInstance().init();
        KeyHandler.register();
        ChestOpenHandler.register();
        ChestMemoryHud.register();
        WorldEventHandler.register();

        LOGGER.info("[ChestMemory] Ready.");
    }
}
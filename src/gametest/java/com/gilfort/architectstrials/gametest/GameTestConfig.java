package com.gilfort.architectstrials.gametest;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Adjusts the server config for GameTest runs.
 * <p>
 * GameTests run in parallel batches and many of them create instances in the same theme dimension, so the
 * instance limit (default 4) is lifted. Tests that check the limit set it themselves and restore it afterwards.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class GameTestConfig {

    private GameTestConfig() {
    }

    /**
     * Lifts the instance limit once the server has started.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ArchitectsTrialsConfig.MAX_CONCURRENT_INSTANCES.set(0);
    }
}

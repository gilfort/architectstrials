package com.gilfort.architectstrials.structure;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Registers the structure pool loader and validates the pool.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeStructureEvents {

    private ChallengeStructureEvents() {
    }

    /**
     * Registers the challenge structure metadata loader.
     *
     * @param event the reload listener registration event
     */
    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(ChallengeStructureLoader.ID, new ChallengeStructureLoader());
    }

    /**
     * Validates the pool once the server's structure templates are available.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ChallengeStructures.validate(event.getServer());
    }

    /**
     * Re-validates the pool after {@code /reload}. The event fires without a player only for reloads.
     *
     * @param event the datapack sync event
     */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            ChallengeStructures.validate(event.getPlayerList().getServer());
        }
    }
}

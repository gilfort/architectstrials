package com.gilfort.architectstrials.structure;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Registers the structure pool loader.
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
}

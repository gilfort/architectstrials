package com.gilfort.architectstrials.structure;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.sub.SubStructures;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Registers the structure pool loader. Templates are checked lazily when a structure is drawn
 * ({@link ChallengeStructures#drawEnterable}), so a (re)load never loads all templates into memory.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeStructureEvents {

    private ChallengeStructureEvents() {
    }

    /**
     * Registers the challenge structure and sub structure (US-32) metadata loaders.
     *
     * @param event the reload listener registration event
     */
    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(ChallengeStructureLoader.ID, new ChallengeStructureLoader());
        event.addListener(SubStructures.Loader.ID, new SubStructures.Loader());
    }
}

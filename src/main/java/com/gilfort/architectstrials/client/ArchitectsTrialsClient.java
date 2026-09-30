package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point of the Architect's Trials mod.
 * <p>
 * This class is never loaded on a dedicated server, so referencing client classes here is safe.
 */
@Mod(value = ArchitectsTrials.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID, value = Dist.CLIENT)
public final class ArchitectsTrialsClient {

    /**
     * Creates the client-side mod instance and registers the config screen (Mods menu).
     *
     * @param modEventBus  the mod-specific event bus, injected by FML
     * @param modContainer the container describing this mod, injected by FML
     */
    public ArchitectsTrialsClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * Runs client-only setup.
     *
     * @param event the client setup event
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ArchitectsTrials.LOGGER.info("Architect's Trials client setup complete.");
    }
}

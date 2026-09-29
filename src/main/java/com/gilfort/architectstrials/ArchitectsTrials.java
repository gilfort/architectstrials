package com.gilfort.architectstrials;

import org.slf4j.Logger;

import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModCreativeModeTabs;
import com.gilfort.architectstrials.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Main entry point of the Architect's Trials mod.
 * <p>
 * Constructed by FML during mod loading on both physical sides. Responsible for wiring all
 * deferred registers and lifecycle listeners onto the mod event bus.
 */
@Mod(ArchitectsTrials.MOD_ID)
public final class ArchitectsTrials {

    /** The mod id; must match {@code mod_id} in {@code gradle.properties}. */
    public static final String MOD_ID = "architectstrials";

    /** Shared logger for the whole mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Creates the mod instance and registers all content.
     *
     * @param modEventBus  the mod-specific event bus, injected by FML
     * @param modContainer the container describing this mod, injected by FML
     */
    public ArchitectsTrials(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModAttachments.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    /**
     * Runs common (both sides) setup after registration has finished.
     *
     * @param event the common setup event
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Architect's Trials common setup complete.");
    }

    /**
     * Creates an {@link Identifier} within this mod's namespace.
     *
     * @param path the path part of the identifier
     * @return the identifier {@code architectstrials:<path>}
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

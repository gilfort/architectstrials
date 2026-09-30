package com.gilfort.architectstrials;

import org.slf4j.Logger;

import com.gilfort.architectstrials.block.ExitMarkerBlock;
import com.gilfort.architectstrials.block.PlayerSpawnMarkerBlock;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModCreativeModeTabs;
import com.gilfort.architectstrials.registry.ModCriteriaTriggers;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModEntityTypes;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        ModDataComponents.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModCriteriaTriggers.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ArchitectsTrialsConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
    }

    /**
     * Runs common (both sides) setup after registration has finished: registers the marker resolvers.
     *
     * @param event the common setup event
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MarkerResolvers.register(ModBlocks.PLAYER_SPAWN_MARKER.get(), PlayerSpawnMarkerBlock::resolve);
            MarkerResolvers.register(ModBlocks.EXIT_MARKER.get(), ExitMarkerBlock::resolve);
            MarkerResolvers.register(ModBlocks.DIRECT_SPAWN_MARKER.get(), SpawnMarkerResolvers::resolveDirect);
            MarkerResolvers.register(ModBlocks.SPAWNER_MARKER.get(), SpawnMarkerResolvers::resolveSpawner);
        });
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

package com.gilfort.architectstrials.client;

import java.util.List;
import java.util.Map;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ExitCamouflage;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModEntityTypes;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
     * Registers entity and block entity renderers: animated surfaces for entry portals and challenge exits;
     * the rune echo is particle-only and uses a no-op renderer.
     *
     * @param event the renderer registration event
     */
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.CHALLENGE_PORTAL.get(), ChallengePortalRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.PORTAL_ECHO.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CHALLENGE_EXIT.get(), ChallengeExitRenderer::new);
    }

    /**
     * Registers the screens of the mod's menus.
     *
     * @param event the menu screen registration event
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SPAWN_MARKER.get(), SpawnMarkerScreen::new);
        event.register(ModMenuTypes.EXIT_MARKER.get(), ExitMarkerScreen::new);
        event.register(ModMenuTypes.VAULT_MARKER.get(), VaultMarkerScreen::new);
        event.register(ModMenuTypes.TRIAL_SPAWNER_MARKER.get(), TrialSpawnerMarkerScreen::new);
        event.register(ModMenuTypes.EQUIPMENT_LIST.get(), EquipmentListScreen::new);
        event.register(ModMenuTypes.LOOT_SETUP.get(), LootSetupScreen::new);
    }

    /**
     * Wraps the models of camouflaged exit states (US-34) so they draw the camouflage block from the block entity's
     * model data below the state's overlay model.
     *
     * @param event the baking result event
     */
    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (Block block : List.of(ModBlocks.EXIT_MARKER.get(), ModBlocks.CHALLENGE_EXIT.get())) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockStateModel overlay = models.get(state);
                BlockStateModel plain = models.get(state.setValue(ExitCamouflage.CAMOUFLAGED, false));
                if (state.getValue(ExitCamouflage.CAMOUFLAGED) && overlay != null && plain != null) {
                    models.put(state, new CamouflageModel(overlay, plain, models));
                }
            }
        }
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

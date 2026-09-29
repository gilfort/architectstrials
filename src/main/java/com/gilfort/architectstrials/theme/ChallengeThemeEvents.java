package com.gilfort.architectstrials.theme;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Game event hooks that keep {@link ChallengeThemes} in sync with the loaded datapacks and enforce
 * theme-wide dimension rules.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeThemeEvents {

    private ChallengeThemeEvents() {
    }

    /**
     * Registers the challenge dimension list loader.
     *
     * @param event the reload listener registration event
     */
    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(ChallengeDimensionListLoader.ID, new ChallengeDimensionListLoader());
    }

    /**
     * Resolves the themes once all dimensions of the server exist.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ChallengeThemes.resolve(event.getServer());
    }

    /**
     * Re-resolves the themes after {@code /reload}. The event fires without a player only for reloads.
     *
     * @param event the datapack sync event
     */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            ChallengeThemes.resolve(event.getPlayerList().getServer());
        }
    }

    /**
     * Clears all themes when the server stops, so no state leaks into the next singleplayer world.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ChallengeThemes.clear();
    }

    /**
     * Prevents natural mob spawning in challenge dimensions, independent of the biome a theme uses.
     * Encounters inside challenges are controlled exclusively by the builder's spawn markers.
     *
     * @param event the spawn placement check
     */
    @SubscribeEvent
    static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (ChallengeThemes.isChallengeDimension(level.dimension())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}

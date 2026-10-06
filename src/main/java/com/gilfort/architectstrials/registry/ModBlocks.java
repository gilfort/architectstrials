package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.block.ExitMarkerBlock;
import com.gilfort.architectstrials.block.PlayerSpawnMarkerBlock;
import com.gilfort.architectstrials.block.SpawnMarkerBlock;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlock;
import com.gilfort.architectstrials.editor.EditorPlatformBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all blocks added by Architect's Trials.
 * <p>
 * Every block needs a matching {@code block.architectstrials.<name>} entry in {@code en_us.json}.
 */
public final class ModBlocks {

    /** Deferred register for blocks in the {@code architectstrials} namespace. */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArchitectsTrials.MOD_ID);

    /** Editor marker for player entry points; resolved to air when a structure is placed. */
    public static final DeferredBlock<PlayerSpawnMarkerBlock> PLAYER_SPAWN_MARKER = BLOCKS.registerBlock(
            "player_spawn_marker", PlayerSpawnMarkerBlock::new, ModBlocks::markerProperties);

    /** Editor marker for challenge exits; resolved to {@link #CHALLENGE_EXIT} when a structure is placed. */
    public static final DeferredBlock<ExitMarkerBlock> EXIT_MARKER = BLOCKS.registerBlock(
            "exit_marker", ExitMarkerBlock::new, ModBlocks::markerProperties);

    /** Editor marker that spawns its configured mobs directly when a structure is placed. */
    public static final DeferredBlock<SpawnMarkerBlock> DIRECT_SPAWN_MARKER = BLOCKS.registerBlock(
            "direct_spawn_marker", SpawnMarkerBlock::new, properties -> markerProperties(properties).mapColor(MapColor.COLOR_RED));

    /** Editor marker that becomes a vanilla monster spawner when a structure is placed. */
    public static final DeferredBlock<SpawnMarkerBlock> SPAWNER_MARKER = BLOCKS.registerBlock(
            "spawner_marker", SpawnMarkerBlock::new, properties -> markerProperties(properties).mapColor(MapColor.COLOR_ORANGE));

    /** Editor marker that becomes a vanilla trial spawner (never ominous) when a structure is placed. */
    public static final DeferredBlock<TrialSpawnerMarkerBlock> TRIAL_SPAWNER_MARKER = BLOCKS.registerBlock(
            "trial_spawner_marker", TrialSpawnerMarkerBlock::new, properties -> markerProperties(properties).mapColor(MapColor.COLOR_CYAN));

    /** The functional challenge exit (redstone signal locks it). Only created by the exit marker resolver. */
    public static final DeferredBlock<ChallengeExitBlock> CHALLENGE_EXIT = BLOCKS.registerBlock(
            "challenge_exit", ChallengeExitBlock::new, properties -> markerProperties(properties)
                    .mapColor(MapColor.COLOR_PURPLE).sound(SoundType.STONE).lightLevel(state -> state.getValue(ChallengeExitBlock.POWERED) || state.getValue(ChallengeExitBlock.SEALED) ? 0 : 10));

    /**
     * Invisible, unbreakable block filling the portal space of challenge exits; walking into it completes the run
     * (no item).
     */
    public static final DeferredBlock<ChallengeExitPortalBlock> CHALLENGE_EXIT_PORTAL = BLOCKS.registerBlock(
            "challenge_exit_portal", ChallengeExitPortalBlock::new, properties -> properties
                    .strength(-1.0F, 3_600_000.0F)
                    .noCollision()
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE)
                    .isValidSpawn((state, level, pos, entityType) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    /** Glass-like start platform of the editor dimension; ignored when saving structures. */
    public static final DeferredBlock<EditorPlatformBlock> EDITOR_PLATFORM = BLOCKS.registerBlock(
            "editor_platform", EditorPlatformBlock::new, properties -> properties
                    .strength(0.3F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .noLootTable()
                    .isValidSpawn((state, level, pos, entityType) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    private ModBlocks() {
    }

    /**
     * Shared properties of editor marker blocks: unbreakable in survival, not movable, no loot.
     *
     * @param properties the base properties
     * @return the marker properties
     */
    private static BlockBehaviour.Properties markerProperties(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.AMETHYST)
                .noLootTable()
                .pushReaction(PushReaction.IMMOVEABLE);
    }

    /**
     * Attaches the block register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

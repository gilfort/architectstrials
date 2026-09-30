package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all block entity types of Architect's Trials.
 */
public final class ModBlockEntityTypes {

    /** Deferred register for block entity types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArchitectsTrials.MOD_ID);

    /** Block entity of the challenge exit (bonus loot table, portal rendering). */
    public static final Supplier<BlockEntityType<ChallengeExitBlockEntity>> CHALLENGE_EXIT = BLOCK_ENTITY_TYPES.register(
            "challenge_exit", () -> new BlockEntityType<>(ChallengeExitBlockEntity::new, ModBlocks.CHALLENGE_EXIT.get()));

    /** Block entity of the exit marker (optional bonus loot table). */
    public static final Supplier<BlockEntityType<ExitMarkerBlockEntity>> EXIT_MARKER = BLOCK_ENTITY_TYPES.register(
            "exit_marker", () -> new BlockEntityType<>(ExitMarkerBlockEntity::new, ModBlocks.EXIT_MARKER.get()));

    /** Block entity shared by the direct spawn and spawner markers (spawn egg + equipment). */
    public static final Supplier<BlockEntityType<SpawnMarkerBlockEntity>> SPAWN_MARKER = BLOCK_ENTITY_TYPES.register(
            "spawn_marker", () -> new BlockEntityType<>(SpawnMarkerBlockEntity::new, ModBlocks.DIRECT_SPAWN_MARKER.get(),
                    ModBlocks.SPAWNER_MARKER.get()));

    /** Block entity of the trial spawner marker (three mob rows, simultaneous mobs, reward loot table). */
    public static final Supplier<BlockEntityType<TrialSpawnerMarkerBlockEntity>> TRIAL_SPAWNER_MARKER = BLOCK_ENTITY_TYPES.register(
            "trial_spawner_marker", () -> new BlockEntityType<>(TrialSpawnerMarkerBlockEntity::new, ModBlocks.TRIAL_SPAWNER_MARKER.get()));

    private ModBlockEntityTypes() {
    }

    /**
     * Attaches the block entity type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}

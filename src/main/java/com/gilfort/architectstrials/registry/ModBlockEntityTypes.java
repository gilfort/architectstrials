package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;

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

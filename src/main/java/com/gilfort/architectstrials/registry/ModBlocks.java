package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.PlayerSpawnMarkerBlock;

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

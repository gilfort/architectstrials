package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all blocks added by Architect's Trials.
 * <p>
 * Every block needs a matching {@code block.architectstrials.<name>} entry in {@code en_us.json}.
 */
public final class ModBlocks {

    /** Deferred register for blocks in the {@code architectstrials} namespace. */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArchitectsTrials.MOD_ID);

    private ModBlocks() {
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

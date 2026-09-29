package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all items (including block items) added by Architect's Trials.
 * <p>
 * Every item needs a matching {@code item.architectstrials.<name>} entry in {@code en_us.json}.
 */
public final class ModItems {

    /** Deferred register for items in the {@code architectstrials} namespace. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArchitectsTrials.MOD_ID);

    private ModItems() {
    }

    /**
     * Attaches the item register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.scroll.ScrollTarget;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all item data components of Architect's Trials.
 */
public final class ModDataComponents {

    /** Deferred register for data component types in the {@code architectstrials} namespace. */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ArchitectsTrials.MOD_ID);

    /** The theme and tier a challenge scroll opens ({@code architectstrials:challenge}). */
    public static final Supplier<DataComponentType<ScrollTarget>> SCROLL_TARGET = DATA_COMPONENTS.registerComponentType(
            "challenge", builder -> builder.persistent(ScrollTarget.CODEC).networkSynchronized(ScrollTarget.STREAM_CODEC));

    private ModDataComponents() {
    }

    /**
     * Attaches the data component register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}

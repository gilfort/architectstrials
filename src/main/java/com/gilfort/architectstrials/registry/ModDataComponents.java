package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.scroll.ScrollTarget;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
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

    /**
     * Challenge time limit in minutes ({@code architectstrials:time_limit}); scrolls without it use the configured
     * default.
     */
    public static final Supplier<DataComponentType<Integer>> TIME_LIMIT = DATA_COMPONENTS.registerComponentType(
            "time_limit", builder -> builder.persistent(ExtraCodecs.POSITIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Multiplayer options of a scroll ({@code architectstrials:options}); absent = solo default. */
    public static final Supplier<DataComponentType<ScrollOptions>> SCROLL_OPTIONS = DATA_COMPONENTS.registerComponentType(
            "options", builder -> builder.persistent(ScrollOptions.CODEC).networkSynchronized(ScrollOptions.STREAM_CODEC));

    /** Effect upgrades of a scroll ({@code architectstrials:effects}); absent = none. */
    public static final Supplier<DataComponentType<ScrollEffects>> SCROLL_EFFECTS = DATA_COMPONENTS.registerComponentType(
            "effects", builder -> builder.persistent(ScrollEffects.CODEC).networkSynchronized(ScrollEffects.STREAM_CODEC));

    /** The area selected with the selection tool ({@code architectstrials:selection}). */
    public static final Supplier<DataComponentType<Selection>> SELECTION = DATA_COMPONENTS.registerComponentType(
            "selection", builder -> builder.persistent(Selection.CODEC).networkSynchronized(Selection.STREAM_CODEC));

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

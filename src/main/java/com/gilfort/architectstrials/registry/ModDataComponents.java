package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.marker.SpawnMarkerClipboard;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollModifiers;
import com.gilfort.architectstrials.scroll.ScrollTarget;
import com.gilfort.architectstrials.sub.SubStructureSetup;

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

    /**
     * How a scroll modifies the run settings of the challenge it opens ({@code architectstrials:modifiers}, US-41);
     * absent = the challenge's own values.
     */
    public static final Supplier<DataComponentType<ScrollModifiers>> SCROLL_MODIFIERS = DATA_COMPONENTS.registerComponentType(
            "modifiers", builder -> builder.persistent(ScrollModifiers.CODEC).networkSynchronized(ScrollModifiers.STREAM_CODEC));

    /** Effect upgrades of a scroll ({@code architectstrials:effects}); absent = none. */
    public static final Supplier<DataComponentType<ScrollEffects>> SCROLL_EFFECTS = DATA_COMPONENTS.registerComponentType(
            "effects", builder -> builder.persistent(ScrollEffects.CODEC).networkSynchronized(ScrollEffects.STREAM_CODEC));

    /** The area selected with the selection tool ({@code architectstrials:selection}). */
    public static final Supplier<DataComponentType<Selection>> SELECTION = DATA_COMPONENTS.registerComponentType(
            "selection", builder -> builder.persistent(Selection.CODEC).networkSynchronized(Selection.STREAM_CODEC));

    /** Loot setup stored in the loot tool for pasting ({@code architectstrials:loot_clipboard}). */
    public static final Supplier<DataComponentType<LootSetup>> LOOT_CLIPBOARD = DATA_COMPONENTS.registerComponentType(
            "loot_clipboard", builder -> builder.persistent(LootSetup.CODEC).networkSynchronized(LootSetup.STREAM_CODEC));

    /** Sub structure setup stored in the sub structure tool for pasting ({@code architectstrials:sub_structure_clipboard}). */
    public static final Supplier<DataComponentType<SubStructureSetup>> SUB_STRUCTURE_CLIPBOARD = DATA_COMPONENTS.registerComponentType(
            "sub_structure_clipboard", builder -> builder.persistent(SubStructureSetup.CODEC).networkSynchronized(SubStructureSetup.STREAM_CODEC));

    /** Spawn marker configuration stored in the spawn marker tool for pasting ({@code architectstrials:spawn_marker_clipboard}, US-39). */
    public static final Supplier<DataComponentType<SpawnMarkerClipboard>> SPAWN_MARKER_CLIPBOARD = DATA_COMPONENTS.registerComponentType(
            "spawn_marker_clipboard", builder -> builder.persistent(SpawnMarkerClipboard.CODEC).networkSynchronized(SpawnMarkerClipboard.STREAM_CODEC));

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

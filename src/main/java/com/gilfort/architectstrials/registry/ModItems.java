package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.MarkerBlockItem;
import com.gilfort.architectstrials.editor.SelectionToolItem;
import com.gilfort.architectstrials.loot.LootToolItem;
import com.gilfort.architectstrials.marker.SpawnMarkerToolItem;
import com.gilfort.architectstrials.scroll.ChallengeScrollItem;
import com.gilfort.architectstrials.sub.SubStructureToolItem;

import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all items (including block items) added by Architect's Trials.
 * <p>
 * Every item needs a matching {@code item.architectstrials.<name>} entry in {@code en_us.json}.
 */
public final class ModItems {

    /** Deferred register for items in the {@code architectstrials} namespace. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArchitectsTrials.MOD_ID);

    /** Item of the {@link ModBlocks#PLAYER_SPAWN_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> PLAYER_SPAWN_MARKER = ITEMS.registerItem("player_spawn_marker",
            properties -> new MarkerBlockItem(ModBlocks.PLAYER_SPAWN_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#EXIT_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> EXIT_MARKER = ITEMS.registerItem("exit_marker",
            properties -> new MarkerBlockItem(ModBlocks.EXIT_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#DIRECT_SPAWN_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> DIRECT_SPAWN_MARKER = ITEMS.registerItem("direct_spawn_marker",
            properties -> new MarkerBlockItem(ModBlocks.DIRECT_SPAWN_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#SPAWNER_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> SPAWNER_MARKER = ITEMS.registerItem("spawner_marker",
            properties -> new MarkerBlockItem(ModBlocks.SPAWNER_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#TRIAL_SPAWNER_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> TRIAL_SPAWNER_MARKER = ITEMS.registerItem("trial_spawner_marker",
            properties -> new MarkerBlockItem(ModBlocks.TRIAL_SPAWNER_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#VAULT_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> VAULT_MARKER = ITEMS.registerItem("vault_marker",
            properties -> new MarkerBlockItem(ModBlocks.VAULT_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Item of the {@link ModBlocks#SUB_STRUCTURE_MARKER}; creative/editor only, no recipe. */
    public static final DeferredItem<MarkerBlockItem> SUB_STRUCTURE_MARKER = ITEMS.registerItem("sub_structure_marker",
            properties -> new MarkerBlockItem(ModBlocks.SUB_STRUCTURE_MARKER.get(), properties.rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    /** Selection tool for importing world areas into the editor; creative/editor only, no recipe. */
    public static final DeferredItem<SelectionToolItem> SELECTION_TOOL = ITEMS.registerItem("selection_tool",
            properties -> new SelectionToolItem(properties.stacksTo(1).rarity(Rarity.EPIC)));

    /** Loot tool for composed loot setups; creative/editor only, no recipe. */
    public static final DeferredItem<LootToolItem> LOOT_TOOL = ITEMS.registerItem("loot_tool",
            properties -> new LootToolItem(properties.stacksTo(1).rarity(Rarity.EPIC)));

    /** Tool copying sub structure setups between markers (US-32); creative/editor only, no recipe. */
    public static final DeferredItem<SubStructureToolItem> SUB_STRUCTURE_TOOL = ITEMS.registerItem("sub_structure_tool",
            properties -> new SubStructureToolItem(properties.stacksTo(1).rarity(Rarity.EPIC)));

    /** Tool copying spawn marker configurations between markers of the same type (US-39); creative/editor only, no recipe. */
    public static final DeferredItem<SpawnMarkerToolItem> SPAWN_MARKER_TOOL = ITEMS.registerItem("spawn_marker_tool",
            properties -> new SpawnMarkerToolItem(properties.stacksTo(1).rarity(Rarity.EPIC)));

    /** The challenge scroll; theme and tier come from the {@code architectstrials:challenge} data component. */
    public static final DeferredItem<ChallengeScrollItem> CHALLENGE_SCROLL = ITEMS.registerItem("challenge_scroll",
            properties -> new ChallengeScrollItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON)));

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

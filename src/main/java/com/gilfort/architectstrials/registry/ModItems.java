package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.MarkerBlockItem;
import com.gilfort.architectstrials.scroll.ChallengeScrollItem;

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

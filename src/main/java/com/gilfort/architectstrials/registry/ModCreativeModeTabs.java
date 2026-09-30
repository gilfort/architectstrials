package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for the creative mode tabs of Architect's Trials.
 */
public final class ModCreativeModeTabs {

    /** Deferred register for creative mode tabs in the {@code architectstrials} namespace. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArchitectsTrials.MOD_ID);

    /**
     * The main creative tab. Automatically lists every item registered in {@link ModItems#ITEMS}, except the
     * challenge scroll, which is only meaningful with a theme and tier (see {@code /architectstrials scroll give}).
     * The icon is a vanilla placeholder until the mod has its own signature item.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.architectstrials.main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> Items.STRUCTURE_BLOCK.getDefaultInstance())
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().stream()
                                    .filter(item -> item != ModItems.CHALLENGE_SCROLL)
                                    .forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeModeTabs() {
    }

    /**
     * Attaches the creative tab register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}

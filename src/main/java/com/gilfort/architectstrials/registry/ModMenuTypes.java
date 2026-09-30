package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.menu.SpawnMarkerMenu;
import com.gilfort.architectstrials.menu.TrialSpawnerMarkerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all menu types of Architect's Trials.
 */
public final class ModMenuTypes {

    /** Deferred register for menu types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, ArchitectsTrials.MOD_ID);

    /** Menu of the direct spawn and spawner markers. */
    public static final Supplier<MenuType<SpawnMarkerMenu>> SPAWN_MARKER = MENU_TYPES.register(
            "spawn_marker", () -> new MenuType<>(SpawnMarkerMenu::new, FeatureFlags.VANILLA_SET));

    /** Menu of the trial spawner marker. */
    public static final Supplier<MenuType<TrialSpawnerMarkerMenu>> TRIAL_SPAWNER_MARKER = MENU_TYPES.register(
            "trial_spawner_marker", () -> new MenuType<>(TrialSpawnerMarkerMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenuTypes() {
    }

    /**
     * Attaches the menu type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}

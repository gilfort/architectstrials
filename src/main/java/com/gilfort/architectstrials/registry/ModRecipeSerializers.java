package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.scroll.ScrollTierRecipe;
import com.gilfort.architectstrials.scroll.ScrollUpgradeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for the recipe serializers of Architect's Trials.
 */
public final class ModRecipeSerializers {

    /** Deferred register for recipe serializers in the {@code architectstrials} namespace. */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ArchitectsTrials.MOD_ID);

    /** Smithing recipe type {@code architectstrials:scroll_upgrade}. */
    public static final Supplier<RecipeSerializer<ScrollUpgradeRecipe>> SCROLL_UPGRADE = RECIPE_SERIALIZERS.register("scroll_upgrade",
            () -> new RecipeSerializer<>(ScrollUpgradeRecipe.MAP_CODEC, ScrollUpgradeRecipe.STREAM_CODEC));

    /** Smithing recipe type {@code architectstrials:scroll_tier} (US-33). */
    public static final Supplier<RecipeSerializer<ScrollTierRecipe>> SCROLL_TIER = RECIPE_SERIALIZERS.register("scroll_tier",
            () -> new RecipeSerializer<>(ScrollTierRecipe.MAP_CODEC, ScrollTierRecipe.STREAM_CODEC));

    private ModRecipeSerializers() {
    }

    /**
     * Attaches the recipe serializer register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}

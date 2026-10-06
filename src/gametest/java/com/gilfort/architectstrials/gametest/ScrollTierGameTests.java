package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.scroll.ScrollTarget;
import com.gilfort.architectstrials.scroll.ScrollTierRecipe;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-33 (scroll tier recipes). The test datapack defines {@code scroll_tier} recipes for the nether
 * tiers 1–3 and 99 (no challenge of tier 99 exists).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollTierGameTests {

    private ScrollTierGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("scroll_tier_recipes")),
                (Consumer<GameTestHelper>) ScrollTierGameTests::recipes));
    }

    /**
     * Blank scroll → tier 1; tier n − 1 → n keeping all components; no result for the wrong base, the wrong theme
     * or a tier without challenges; a scroll target without tier means tier 1.
     */
    private static void recipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ScrollTierRecipe tier1 = recipe(level, 1);
        ScrollTierRecipe tier2 = recipe(level, 2);
        ScrollTierRecipe tier99 = recipe(level, 99);
        Identifier nether = Level.NETHER.identifier();

        ItemStack blank = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        helper.assertTrue(tier1.matches(input(blank), level), "Tier 1 recipe does not match the blank scroll");
        ItemStack first = tier1.assemble(input(blank));
        helper.assertTrue(new ScrollTarget(nether, 1).equals(first.get(ModDataComponents.SCROLL_TARGET.get())), "Blank scroll did not become tier 1");
        helper.assertFalse(tier1.matches(input(first), level), "Tier 1 recipe matched an already bound scroll");

        first.set(ModDataComponents.SCROLL_OPTIONS.get(), new ScrollOptions(4, 0, false));
        first.set(ModDataComponents.TIME_LIMIT.get(), 9);
        helper.assertFalse(tier2.matches(input(blank), level), "Tier 2 recipe matched the blank scroll");
        ItemStack second = tier2.assemble(input(first));
        helper.assertTrue(new ScrollTarget(nether, 2).equals(second.get(ModDataComponents.SCROLL_TARGET.get())), "Tier 1 scroll was not raised to tier 2");
        helper.assertTrue(new ScrollOptions(4, 0, false).equals(second.get(ModDataComponents.SCROLL_OPTIONS.get()))
                && Integer.valueOf(9).equals(second.get(ModDataComponents.TIME_LIMIT.get())), "Components were lost when raising the tier");
        helper.assertFalse(tier2.matches(input(second), level), "Tier 2 recipe matched a tier 2 scroll");

        ItemStack otherTheme = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        otherTheme.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(ArchitectsTrials.id("gametest_theme"), 1));
        helper.assertFalse(tier2.matches(input(otherTheme), level), "Tier 2 recipe matched a scroll of another theme");

        ItemStack almost = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        almost.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(nether, 98));
        helper.assertFalse(tier99.matches(input(almost), level), "Recipe produced a tier without challenges");
        helper.assertFalse(tier2.display().isEmpty(), "Recipe has no recipe book display");

        ScrollTarget withoutTier = ScrollTarget.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"theme\": \"minecraft:the_nether\"}")).getOrThrow();
        helper.assertTrue(withoutTier.tier() == 1, "Scroll target without tier is not tier 1");
        helper.succeed();
    }

    private static ScrollTierRecipe recipe(ServerLevel level, int tier) {
        return (ScrollTierRecipe) level.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, ArchitectsTrials.id("gametest_scroll_tier_nether_" + tier)))
                .orElseThrow(() -> new IllegalStateException("Scroll tier recipe " + tier + " not loaded")).value();
    }

    private static SmithingRecipeInput input(ItemStack scroll) {
        return new SmithingRecipeInput(new ItemStack(Items.PAPER), scroll, new ItemStack(Items.NETHERRACK));
    }
}

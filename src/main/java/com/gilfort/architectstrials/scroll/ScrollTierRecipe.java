package com.gilfort.architectstrials.scroll;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.registry.ModRecipeSerializers;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * Smithing recipe {@code architectstrials:scroll_tier} (US-33): one step of a theme's scroll progression.
 * <pre>{@code
 * {
 *   "type": "architectstrials:scroll_tier",
 *   "theme": "mypack:crypt",
 *   "tier": 2,
 *   "template": "minecraft:paper",
 *   "addition": "minecraft:bone_block"
 * }
 * }</pre>
 * Tier 1 turns the Blank Challenge Scroll into a tier 1 scroll of the theme. Tier n > 1 takes a scroll of the theme
 * with tier n − 1 and raises it to n, keeping all other components (modifiers, effects, name, …). There is
 * no result if the theme has no challenge of the tier.
 */
public class ScrollTierRecipe extends SimpleSmithingRecipe {

    /** Persistent codec. */
    public static final MapCodec<ScrollTierRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            Identifier.CODEC.fieldOf("theme").forGetter(ScrollTierRecipe::theme),
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(ScrollTierRecipe::tier),
            Ingredient.CODEC.optionalFieldOf("template").forGetter(ScrollTierRecipe::templateIngredient),
            Ingredient.CODEC.optionalFieldOf("addition").forGetter(ScrollTierRecipe::additionIngredient)
    ).apply(instance, ScrollTierRecipe::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollTierRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            Identifier.STREAM_CODEC, ScrollTierRecipe::theme,
            ByteBufCodecs.VAR_INT, ScrollTierRecipe::tier,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollTierRecipe::templateIngredient,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollTierRecipe::additionIngredient,
            ScrollTierRecipe::new);

    private final Identifier theme;
    private final int tier;
    private final Optional<Ingredient> template;
    private final Optional<Ingredient> addition;
    private boolean warnedUnknownTheme;

    /**
     * Creates the recipe.
     *
     * @param commonInfo the common recipe info
     * @param theme      the theme of the scroll
     * @param tier       the tier the recipe produces
     * @param template   the template ingredient
     * @param addition   the addition ingredient
     */
    public ScrollTierRecipe(Recipe.CommonInfo commonInfo, Identifier theme, int tier, Optional<Ingredient> template, Optional<Ingredient> addition) {
        super(commonInfo);
        this.theme = theme;
        this.tier = tier;
        this.template = template;
        this.addition = addition;
    }

    /** @return the theme of the scroll */
    public Identifier theme() {
        return this.theme;
    }

    /** @return the tier the recipe produces */
    public int tier() {
        return this.tier;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return super.matches(input, level) && !this.assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack scroll = input.base();
        if (!scroll.is(ModItems.CHALLENGE_SCROLL.get()) || !this.themeAvailable()) {
            return ItemStack.EMPTY;
        }
        ScrollTarget current = scroll.get(ModDataComponents.SCROLL_TARGET.get());
        boolean fits = this.tier == 1 ? current == null
                : current != null && current.theme().equals(this.theme) && current.tier() == this.tier - 1;
        if (!fits || !ChallengeStructures.tiers(this.theme).contains(this.tier)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = scroll.copyWithCount(1);
        result.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(this.theme, this.tier));
        return result;
    }

    private boolean themeAvailable() {
        if (ChallengeThemes.get(this.theme).isPresent()) {
            return true;
        }
        if (!this.warnedUnknownTheme) {
            this.warnedUnknownTheme = true;
            ArchitectsTrials.LOGGER.warn("Scroll tier recipe for unknown theme {} never matches", this.theme);
        }
        return false;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return this.template;
    }

    @Override
    public Ingredient baseIngredient() {
        return Ingredient.of(ModItems.CHALLENGE_SCROLL.get());
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<ScrollTierRecipe> getSerializer() {
        return ModRecipeSerializers.SCROLL_TIER.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.baseIngredient()), this.addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(
                Ingredient.optionalIngredientToDisplay(this.template),
                new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(this.tier == 1
                        ? new ItemStack(ModItems.CHALLENGE_SCROLL.get()) : this.scroll(this.tier - 1))),
                Ingredient.optionalIngredientToDisplay(this.addition),
                new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(this.scroll(this.tier))),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }

    private ItemStack scroll(int scrollTier) {
        ItemStack stack = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        stack.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(this.theme, scrollTier));
        return stack;
    }
}

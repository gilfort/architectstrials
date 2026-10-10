package com.gilfort.architectstrials.scroll;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModRecipeSerializers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
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
 * Smithing recipe {@code architectstrials:scroll_upgrade}: template + challenge scroll + addition → the same
 * scroll with all its components (theme, tier, modifiers, …) plus the recipe's upgrades.
 * <pre>{@code
 * {
 *   "type": "architectstrials:scroll_upgrade",
 *   "template": "minecraft:paper",
 *   "base": "architectstrials:challenge_scroll",
 *   "addition": "minecraft:rabbit_foot",
 *   "modifiers": {"time": 25, "max_players": 2},
 *   "effects": [{"target": "player", "effect": "minecraft:luck", "duration": -1}]
 * }
 * }</pre>
 * Modifiers are combined with the scroll's ({@link ScrollModifiers#combine}: percentages multiply, players add up).
 * Effects are merged ({@link ScrollEffects#merge}); if an effect would not improve the scroll (weaker or equal
 * duplicate), or the recipe changes nothing, there is no result.
 */
public class ScrollUpgradeRecipe extends SimpleSmithingRecipe {

    /** Persistent codec. */
    public static final MapCodec<ScrollUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            Ingredient.CODEC.optionalFieldOf("template").forGetter(ScrollUpgradeRecipe::templateIngredient),
            Ingredient.CODEC.fieldOf("base").forGetter(ScrollUpgradeRecipe::baseIngredient),
            Ingredient.CODEC.optionalFieldOf("addition").forGetter(ScrollUpgradeRecipe::additionIngredient),
            ScrollModifiers.CODEC.optionalFieldOf("modifiers", ScrollModifiers.NONE).forGetter(ScrollUpgradeRecipe::modifiers),
            ScrollEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(ScrollUpgradeRecipe::effects)
    ).apply(instance, ScrollUpgradeRecipe::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::templateIngredient,
            Ingredient.CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::baseIngredient,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::additionIngredient,
            ScrollModifiers.STREAM_CODEC, ScrollUpgradeRecipe::modifiers,
            ScrollEffect.STREAM_CODEC.apply(ByteBufCodecs.list()), ScrollUpgradeRecipe::effects,
            ScrollUpgradeRecipe::new);

    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final Optional<Ingredient> addition;
    private final ScrollModifiers modifiers;
    private final List<ScrollEffect> effects;

    /**
     * Creates the recipe.
     *
     * @param commonInfo the common recipe info
     * @param template   the template ingredient
     * @param base       the base ingredient (challenge scrolls)
     * @param addition   the addition ingredient
     * @param modifiers  the modifiers to combine with the scroll's
     * @param effects    the effects to add
     */
    public ScrollUpgradeRecipe(Recipe.CommonInfo commonInfo, Optional<Ingredient> template, Ingredient base, Optional<Ingredient> addition,
            ScrollModifiers modifiers, List<ScrollEffect> effects) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.modifiers = modifiers;
        this.effects = List.copyOf(effects);
    }

    /** @return the modifiers this recipe adds */
    public ScrollModifiers modifiers() {
        return this.modifiers;
    }

    /** @return the effects added by this recipe */
    public List<ScrollEffect> effects() {
        return this.effects;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return super.matches(input, level) && !this.assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack scroll = input.base();
        if (!scroll.has(ModDataComponents.SCROLL_TARGET.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = scroll.copyWithCount(1);
        boolean changed = false;
        if (!this.modifiers.isNeutral()) {
            ScrollModifiers current = scroll.getOrDefault(ModDataComponents.SCROLL_MODIFIERS.get(), ScrollModifiers.NONE);
            ScrollModifiers combined = current.combine(this.modifiers);
            changed = !combined.equals(current);
            result.set(ModDataComponents.SCROLL_MODIFIERS.get(), combined);
        }
        if (!this.effects.isEmpty()) {
            Optional<ScrollEffects> merged = scroll.getOrDefault(ModDataComponents.SCROLL_EFFECTS.get(), ScrollEffects.NONE).merge(this.effects);
            if (merged.isEmpty()) {
                return ItemStack.EMPTY;
            }
            result.set(ModDataComponents.SCROLL_EFFECTS.get(), merged.get());
            changed = true;
        }
        return changed ? result : ItemStack.EMPTY;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return this.template;
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<ScrollUpgradeRecipe> getSerializer() {
        return ModRecipeSerializers.SCROLL_UPGRADE.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(
                Ingredient.optionalIngredientToDisplay(this.template),
                this.base.display(),
                Ingredient.optionalIngredientToDisplay(this.addition),
                this.base.display(),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}

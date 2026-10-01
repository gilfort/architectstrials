package com.gilfort.architectstrials.scroll;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModRecipeSerializers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
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
 * scroll with all its components (theme, tier, time limit, …) plus the recipe's upgrades.
 * <pre>{@code
 * {
 *   "type": "architectstrials:scroll_upgrade",
 *   "template": "minecraft:paper",
 *   "base": "architectstrials:challenge_scroll",
 *   "addition": "minecraft:rabbit_foot",
 *   "options": {"max_players": 4},
 *   "effects": [{"target": "player", "effect": "minecraft:luck", "duration": -1}]
 * }
 * }</pre>
 * Portal options overwrite the scroll's values. Effects are merged ({@link ScrollEffects#merge}); if an effect
 * would not improve the scroll (weaker or equal duplicate), or the recipe changes nothing, there is no result.
 */
public class ScrollUpgradeRecipe extends SimpleSmithingRecipe {

    /** Persistent codec. */
    public static final MapCodec<ScrollUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            Ingredient.CODEC.optionalFieldOf("template").forGetter(ScrollUpgradeRecipe::templateIngredient),
            Ingredient.CODEC.fieldOf("base").forGetter(ScrollUpgradeRecipe::baseIngredient),
            Ingredient.CODEC.optionalFieldOf("addition").forGetter(ScrollUpgradeRecipe::additionIngredient),
            OptionsPatch.CODEC.optionalFieldOf("options", OptionsPatch.NONE).forGetter(ScrollUpgradeRecipe::options),
            ScrollEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(ScrollUpgradeRecipe::effects)
    ).apply(instance, ScrollUpgradeRecipe::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::templateIngredient,
            Ingredient.CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::baseIngredient,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, ScrollUpgradeRecipe::additionIngredient,
            OptionsPatch.STREAM_CODEC, ScrollUpgradeRecipe::options,
            ScrollEffect.STREAM_CODEC.apply(ByteBufCodecs.list()), ScrollUpgradeRecipe::effects,
            ScrollUpgradeRecipe::new);

    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final Optional<Ingredient> addition;
    private final OptionsPatch options;
    private final List<ScrollEffect> effects;

    /**
     * Creates the recipe.
     *
     * @param commonInfo the common recipe info
     * @param template   the template ingredient
     * @param base       the base ingredient (challenge scrolls)
     * @param addition   the addition ingredient
     * @param options    the portal options to set
     * @param effects    the effects to add
     */
    public ScrollUpgradeRecipe(Recipe.CommonInfo commonInfo, Optional<Ingredient> template, Ingredient base, Optional<Ingredient> addition,
            OptionsPatch options, List<ScrollEffect> effects) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.options = options;
        this.effects = List.copyOf(effects);
    }

    /** @return the portal options set by this recipe */
    public OptionsPatch options() {
        return this.options;
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
        if (!this.options.isEmpty()) {
            ScrollOptions current = scroll.getOrDefault(ModDataComponents.SCROLL_OPTIONS.get(), ScrollOptions.DEFAULT);
            ScrollOptions patched = this.options.apply(current);
            changed = !patched.equals(current);
            result.set(ModDataComponents.SCROLL_OPTIONS.get(), patched);
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

    /**
     * Portal options set by an upgrade; absent fields keep the scroll's value.
     *
     * @param maxPlayers        the new maximum number of players
     * @param portalOpenSeconds the new portal open duration
     * @param allowReentry      the new re-entry rule
     */
    public record OptionsPatch(Optional<Integer> maxPlayers, Optional<Integer> portalOpenSeconds, Optional<Boolean> allowReentry) {

        /** A patch that changes nothing. */
        public static final OptionsPatch NONE = new OptionsPatch(Optional.empty(), Optional.empty(), Optional.empty());

        /** Persistent codec, using the same field names as {@link ScrollOptions}. */
        public static final Codec<OptionsPatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_players").forGetter(OptionsPatch::maxPlayers),
                Codec.intRange(ScrollOptions.OPEN_UNTIL_TIME_LIMIT, Integer.MAX_VALUE).optionalFieldOf("portal_open_seconds")
                        .forGetter(OptionsPatch::portalOpenSeconds),
                Codec.BOOL.optionalFieldOf("allow_reentry").forGetter(OptionsPatch::allowReentry)
        ).apply(instance, OptionsPatch::new));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, OptionsPatch> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), OptionsPatch::maxPlayers,
                ByteBufCodecs.optional(ByteBufCodecs.INT), OptionsPatch::portalOpenSeconds,
                ByteBufCodecs.optional(ByteBufCodecs.BOOL), OptionsPatch::allowReentry,
                OptionsPatch::new);

        /** @return {@code true} if the patch changes nothing */
        public boolean isEmpty() {
            return this.maxPlayers.isEmpty() && this.portalOpenSeconds.isEmpty() && this.allowReentry.isEmpty();
        }

        /**
         * Applies the patch.
         *
         * @param options the current options
         * @return the patched options
         */
        public ScrollOptions apply(ScrollOptions options) {
            return new ScrollOptions(this.maxPlayers.orElse(options.maxPlayers()),
                    this.portalOpenSeconds.orElse(options.portalOpenSeconds()), this.allowReentry.orElse(options.allowReentry()));
        }
    }
}

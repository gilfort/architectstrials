package com.gilfort.architectstrials.scroll;

import java.util.ArrayList;
import java.util.List;

import com.gilfort.architectstrials.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * Per-player bookkeeping of what entering a challenge applied (attachment {@code architectstrials:parked_effects}).
 * <p>
 * Effects of scrolls and challenges (US-18, US-36) and challenge attribute modifiers are removed when the player
 * leaves the challenge. Stored on the player (not on the instance) so this also works if the instance is gone
 * meanwhile, e.g. it expired while the player was offline. Effects the player already had of the same type are
 * parked here and given back with their duration from the entry when leaving.
 *
 * @param applied   the effect types applied on entry
 * @param parked    the player's own effects that were replaced by them
 * @param modifiers the attribute modifiers added on entry
 */
public record ParkedEffects(List<Holder<MobEffect>> applied, List<MobEffectInstance> parked, List<Modifier> modifiers) {

    /** Nothing applied or parked. */
    public static final ParkedEffects EMPTY = new ParkedEffects(List.of(), List.of(), List.of());

    /** Persistent codec. */
    public static final MapCodec<ParkedEffects> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LenientCodecs.list(MobEffect.CODEC, "parked effect").optionalFieldOf("applied", List.of()).forGetter(ParkedEffects::applied),
            LenientCodecs.list(MobEffectInstance.CODEC, "parked effect").optionalFieldOf("parked", List.of()).forGetter(ParkedEffects::parked),
            LenientCodecs.list(Modifier.CODEC, "challenge attribute").optionalFieldOf("modifiers", List.of()).forGetter(ParkedEffects::modifiers)
    ).apply(instance, ParkedEffects::new));

    /** Creates the bookkeeping, defensively copying the lists. */
    public ParkedEffects {
        applied = List.copyOf(applied);
        parked = List.copyOf(parked);
        modifiers = List.copyOf(modifiers);
    }

    /** @return {@code true} if nothing is applied or parked */
    public boolean isEmpty() {
        return this.applied.isEmpty() && this.parked.isEmpty() && this.modifiers.isEmpty();
    }

    /**
     * Records an applied effect type and, if present, the player's own effect it replaced. An effect type that is
     * already recorded keeps its originally parked effect.
     *
     * @param type the effect type
     * @param own  the player's own effect of that type, or {@code null}
     * @return the updated bookkeeping
     */
    public ParkedEffects with(Holder<MobEffect> type, @Nullable MobEffectInstance own) {
        if (this.applied.contains(type)) {
            return this;
        }
        List<Holder<MobEffect>> newApplied = new ArrayList<>(this.applied);
        newApplied.add(type);
        List<MobEffectInstance> newParked = new ArrayList<>(this.parked);
        if (own != null) {
            newParked.add(new MobEffectInstance(own));
        }
        return new ParkedEffects(newApplied, newParked, this.modifiers);
    }

    /**
     * Records an attribute modifier added on entry.
     *
     * @param modifier the modifier reference
     * @return the updated bookkeeping
     */
    public ParkedEffects withModifier(Modifier modifier) {
        if (this.modifiers.contains(modifier)) {
            return this;
        }
        List<Modifier> newModifiers = new ArrayList<>(this.modifiers);
        newModifiers.add(modifier);
        return new ParkedEffects(this.applied, this.parked, newModifiers);
    }

    /**
     * An attribute modifier added on entry.
     *
     * @param attribute the attribute
     * @param id        the modifier id
     */
    public record Modifier(Holder<Attribute> attribute, Identifier id) {

        /** Persistent codec. */
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                Identifier.CODEC.fieldOf("id").forGetter(Modifier::id)
        ).apply(instance, Modifier::new));
    }
}

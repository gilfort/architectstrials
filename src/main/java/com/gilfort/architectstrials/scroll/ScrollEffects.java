package com.gilfort.architectstrials.scroll;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.util.LenientCodecs;
import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The effect upgrades of a challenge scroll, stored as data component {@code architectstrials:effects} and fixed
 * into the instance when the portal opens.
 * <pre>{@code "architectstrials:effects": [{"target": "mobs", "effect": "minecraft:speed", "duration": -1}]}</pre>
 *
 * @param entries the effects; at most one per target and effect type
 */
public record ScrollEffects(List<ScrollEffect> entries) {

    /** No effects. */
    public static final ScrollEffects NONE = new ScrollEffects(List.of());

    /** Persistent codec (a plain list). */
    public static final Codec<ScrollEffects> CODEC = LenientCodecs.list(ScrollEffect.CODEC, "scroll effect").xmap(ScrollEffects::new, ScrollEffects::entries);

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollEffects> STREAM_CODEC =
            ScrollEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).map(ScrollEffects::new, ScrollEffects::entries);

    /** Creates the effects, defensively copying the list. */
    public ScrollEffects {
        entries = List.copyOf(entries);
    }

    /**
     * Returns the effects for one target.
     *
     * @param target the target
     * @return the matching effects
     */
    public List<ScrollEffect> forTarget(ScrollEffect.Target target) {
        return this.entries.stream().filter(entry -> entry.target() == target).toList();
    }

    /**
     * Adds upgrade effects. An effect with the same target and type replaces the existing one if it is stronger
     * or lasts longer; if any added effect does not improve on its existing counterpart, the whole upgrade is
     * rejected.
     *
     * @param additions the effects to add
     * @return the merged effects, or empty if the upgrade would not improve the scroll
     */
    public Optional<ScrollEffects> merge(List<ScrollEffect> additions) {
        List<ScrollEffect> merged = new ArrayList<>(this.entries);
        for (ScrollEffect addition : additions) {
            Optional<ScrollEffect> existing = merged.stream().filter(addition::sameSlot).findFirst();
            if (existing.isPresent()) {
                if (!addition.improves(existing.get())) {
                    return Optional.empty();
                }
                merged.set(merged.indexOf(existing.get()), addition);
            } else {
                merged.add(addition);
            }
        }
        return Optional.of(new ScrollEffects(merged));
    }
}

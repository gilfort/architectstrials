package com.gilfort.architectstrials.scroll;

import java.util.ArrayList;
import java.util.List;

import com.gilfort.architectstrials.util.LenientCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;

/**
 * Per-player bookkeeping of infinite scroll effects (attachment {@code architectstrials:parked_effects}).
 * <p>
 * Infinite scroll effects must be removed when the player leaves the instance. Stored on the player (not on the
 * instance) so this also works if the instance is gone meanwhile, e.g. it expired while the player was offline.
 * Effects the player already had of the same type are parked here, paused, and given back on leaving.
 *
 * @param applied the infinite effect types applied by a scroll
 * @param parked  the player's own effects that were replaced by them
 */
public record ParkedEffects(List<Holder<MobEffect>> applied, List<MobEffectInstance> parked) {

    /** Nothing applied or parked. */
    public static final ParkedEffects EMPTY = new ParkedEffects(List.of(), List.of());

    /** Persistent codec. */
    public static final MapCodec<ParkedEffects> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LenientCodecs.list(MobEffect.CODEC, "parked effect").optionalFieldOf("applied", List.of()).forGetter(ParkedEffects::applied),
            LenientCodecs.list(MobEffectInstance.CODEC, "parked effect").optionalFieldOf("parked", List.of()).forGetter(ParkedEffects::parked)
    ).apply(instance, ParkedEffects::new));

    /** Creates the bookkeeping, defensively copying the lists. */
    public ParkedEffects {
        applied = List.copyOf(applied);
        parked = List.copyOf(parked);
    }

    /** @return {@code true} if nothing is applied or parked */
    public boolean isEmpty() {
        return this.applied.isEmpty() && this.parked.isEmpty();
    }

    /**
     * Records an applied infinite effect and, if present, the player's own effect it replaced. An effect type that
     * is already recorded keeps its originally parked effect.
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
        return new ParkedEffects(newApplied, newParked);
    }
}

package com.gilfort.architectstrials.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A mob effect every player gets on entering a challenge (US-36), defined in the challenge metadata.
 *
 * @param effect    the effect
 * @param amplifier the amplifier ({@code 0} = level I)
 * @param duration  the duration in ticks, or {@code -1} for the whole stay (the remaining time of the instance)
 */
public record ChallengeEffect(Holder<MobEffect> effect, int amplifier, int duration) {

    /** Codec for the metadata JSON. */
    public static final Codec<ChallengeEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MobEffect.CODEC.fieldOf("effect").forGetter(ChallengeEffect::effect),
            Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(ChallengeEffect::amplifier),
            Codec.intRange(MobEffectInstance.INFINITE_DURATION, Integer.MAX_VALUE).optionalFieldOf("duration", MobEffectInstance.INFINITE_DURATION)
                    .forGetter(ChallengeEffect::duration)
    ).apply(instance, ChallengeEffect::new));
}

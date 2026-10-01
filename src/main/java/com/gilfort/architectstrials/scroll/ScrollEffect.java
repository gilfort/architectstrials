package com.gilfort.architectstrials.scroll;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

/**
 * One effect upgrade of a challenge scroll: a vanilla (or modded) {@link MobEffect} applied to the players or the
 * mobs of the instance.
 * <pre>{@code {"target": "player", "effect": "minecraft:luck", "duration": -1, "amplifier": 0}}</pre>
 *
 * @param target    who receives the effect
 * @param effect    the effect
 * @param duration  the duration in ticks; {@code -1} = infinite (removed from players when they leave)
 * @param amplifier the amplifier ({@code 0} = level I)
 */
public record ScrollEffect(Target target, Holder<MobEffect> effect, int duration, int amplifier) {

    /** Persistent codec. */
    public static final Codec<ScrollEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Target.CODEC.fieldOf("target").forGetter(ScrollEffect::target),
            MobEffect.CODEC.fieldOf("effect").forGetter(ScrollEffect::effect),
            Codec.intRange(MobEffectInstance.INFINITE_DURATION, Integer.MAX_VALUE).fieldOf("duration").forGetter(ScrollEffect::duration),
            Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(ScrollEffect::amplifier)
    ).apply(instance, ScrollEffect::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrollEffect> STREAM_CODEC = StreamCodec.composite(
            Target.STREAM_CODEC, ScrollEffect::target,
            MobEffect.STREAM_CODEC, ScrollEffect::effect,
            ByteBufCodecs.INT, ScrollEffect::duration,
            ByteBufCodecs.VAR_INT, ScrollEffect::amplifier,
            ScrollEffect::new);

    /** @return a new effect instance to apply */
    public MobEffectInstance instance() {
        return new MobEffectInstance(this.effect, this.duration, this.amplifier);
    }

    /**
     * Checks whether this effect may replace an existing one with the same target and effect: it must be stronger
     * or last longer.
     *
     * @param existing the existing effect
     * @return {@code true} if this effect improves on the existing one
     */
    public boolean improves(ScrollEffect existing) {
        return this.amplifier > existing.amplifier || longer(this.duration, existing.duration);
    }

    /**
     * Checks whether this effect has the same target and effect type as another one.
     *
     * @param other the other effect
     * @return {@code true} if both apply the same effect to the same target
     */
    public boolean sameSlot(ScrollEffect other) {
        return this.target == other.target && this.effect.equals(other.effect);
    }

    /**
     * Describes the effect for tooltips, e.g. "Luck II (5:00)".
     *
     * @param tickRate the current tick rate
     * @return the description
     */
    public Component describe(float tickRate) {
        Component name = Component.translatable(this.effect.value().getDescriptionId());
        if (this.amplifier > 0) {
            name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + this.amplifier));
        }
        return Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(this.instance(), 1.0F, tickRate));
    }

    private static boolean longer(int duration, int other) {
        if (duration == other) {
            return false;
        }
        return duration == MobEffectInstance.INFINITE_DURATION || (other != MobEffectInstance.INFINITE_DURATION && duration > other);
    }

    /** Who receives a scroll effect. */
    public enum Target implements StringRepresentable {
        /** Every player entering the instance (applied on each entry). */
        PLAYER,
        /** Every mob of the instance (applied on the first entry and to mobs spawned afterwards). */
        MOBS;

        /** Codec by lowercase name. */
        public static final Codec<Target> CODEC = StringRepresentable.fromEnum(Target::values);

        /** Network codec. */
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Target::ordinal);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}

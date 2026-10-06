package com.gilfort.architectstrials.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * An attribute modifier every player gets while inside a challenge (US-36), defined in the challenge metadata.
 *
 * @param attribute the attribute
 * @param amount    the modifier amount
 * @param operation how the amount is applied
 */
public record ChallengeAttribute(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {

    /** Codec for the metadata JSON. */
    public static final Codec<ChallengeAttribute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Attribute.CODEC.fieldOf("attribute").forGetter(ChallengeAttribute::attribute),
            Codec.DOUBLE.fieldOf("amount").forGetter(ChallengeAttribute::amount),
            AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(ChallengeAttribute::operation)
    ).apply(instance, ChallengeAttribute::new));
}

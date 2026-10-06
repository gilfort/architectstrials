package com.gilfort.architectstrials.scroll;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * The challenge a scroll opens: a theme and a fixed tier. Stored as data component
 * {@code architectstrials:challenge} on the scroll, e.g. in a recipe result:
 * <pre>{@code "components": {"architectstrials:challenge": {"theme": "mypack:nether", "tier": 2}}}</pre>
 *
 * @param theme the theme (dimension) id
 * @param tier  the tier ({@code >= 1}; {@code 1} if missing)
 */
public record ScrollTarget(Identifier theme, int tier) {

    /** Persistent codec. */
    public static final Codec<ScrollTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("theme").forGetter(ScrollTarget::theme),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("tier", 1).forGetter(ScrollTarget::tier)
    ).apply(instance, ScrollTarget::new));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ScrollTarget> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ScrollTarget::theme,
            ByteBufCodecs.VAR_INT, ScrollTarget::tier,
            ScrollTarget::new);
}

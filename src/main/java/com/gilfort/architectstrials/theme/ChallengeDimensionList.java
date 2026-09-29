package com.gilfort.architectstrials.theme;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Serialized form of a challenge dimension list file,
 * {@code data/<namespace>/architectstrials/challenge_dimensions.json}.
 * <p>
 * Works like a vanilla tag file: all list files of all active datapacks are merged in pack order.
 * A file with {@code "replace": true} discards every entry collected from lower-priority packs first.
 *
 * <pre>{@code
 * {
 *   "replace": false,
 *   "values": ["mypack:nether", "mypack:magic"]
 * }
 * }</pre>
 *
 * @param replace whether entries from lower-priority packs are discarded
 * @param values  the ids of the dimensions to register as challenge themes
 */
public record ChallengeDimensionList(boolean replace, List<Identifier> values) {

    /** Codec for the JSON file format. */
    public static final Codec<ChallengeDimensionList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(ChallengeDimensionList::replace),
            Identifier.CODEC.listOf().fieldOf("values").forGetter(ChallengeDimensionList::values)
    ).apply(instance, ChallengeDimensionList::new));
}

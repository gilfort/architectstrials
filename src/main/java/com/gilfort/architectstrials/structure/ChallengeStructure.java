package com.gilfort.architectstrials.structure;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * Metadata of a stored challenge structure: which theme and tier pool it belongs to and which structure
 * template it places.
 * <p>
 * Stored as {@code data/<ns>/architectstrials/challenge/<theme>/tier_<n>/<id>.json}; the template itself as
 * {@code data/<ns>/structure/challenges/<theme>/tier_<n>/<id>.nbt}. The JSON is authoritative — the folder
 * layout only serves human overview.
 *
 * @param theme     the theme (dimension) id this structure belongs to
 * @param tier      the tier ({@code >= 1})
 * @param structure the id of the structure template to place
 * @param name      optional human-readable name
 * @param author    optional author
 * @param created   optional creation timestamp (epoch milliseconds)
 * @param weight    selection weight within the pool ({@code >= 1}, default 1)
 * @param rotation  whether the structure may be randomly rotated and mirrored on placement (default false)
 */
public record ChallengeStructure(
        Identifier theme,
        int tier,
        Identifier structure,
        Optional<String> name,
        Optional<String> author,
        Optional<Long> created,
        int weight,
        boolean rotation
) {

    /** Codec for the metadata JSON file. */
    public static final Codec<ChallengeStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("theme").forGetter(ChallengeStructure::theme),
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(ChallengeStructure::tier),
            Identifier.CODEC.fieldOf("structure").forGetter(ChallengeStructure::structure),
            Codec.STRING.optionalFieldOf("name").forGetter(ChallengeStructure::name),
            Codec.STRING.optionalFieldOf("author").forGetter(ChallengeStructure::author),
            Codec.LONG.optionalFieldOf("created").forGetter(ChallengeStructure::created),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(ChallengeStructure::weight),
            Codec.BOOL.optionalFieldOf("rotation", false).forGetter(ChallengeStructure::rotation)
    ).apply(instance, ChallengeStructure::new));
}

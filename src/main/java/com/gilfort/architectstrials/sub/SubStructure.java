package com.gilfort.architectstrials.sub;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Metadata of a sub structure (US-32): a reusable piece that Sub Structure Markers place inside challenge
 * structures. Sub structures are not part of any challenge pool and have no theme or tier.
 * <p>
 * Stored as {@code data/<ns>/architectstrials/sub/<id>.json}; the template as
 * {@code data/<ns>/structure/sub/<id>.nbt}. The sub structure's id is {@code <ns>:<id>}.
 *
 * @param structure the id of the structure template to place
 * @param name      optional human-readable name
 * @param author    optional author
 * @param created   optional creation timestamp (epoch milliseconds)
 */
public record SubStructure(Identifier structure, Optional<String> name, Optional<String> author, Optional<Long> created) {

    /** Codec for the metadata JSON file. */
    public static final Codec<SubStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("structure").forGetter(SubStructure::structure),
            Codec.STRING.optionalFieldOf("name").forGetter(SubStructure::name),
            Codec.STRING.optionalFieldOf("author").forGetter(SubStructure::author),
            Codec.LONG.optionalFieldOf("created").forGetter(SubStructure::created)
    ).apply(instance, SubStructure::new));

    /**
     * Returns the template id of a sub structure id.
     *
     * @param id the sub structure id ({@code <ns>:<id>})
     * @return the template id ({@code <ns>:sub/<id>})
     */
    public static Identifier templateId(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "sub/" + id.getPath());
    }
}

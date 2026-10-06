package com.gilfort.architectstrials.structure;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.GameType;

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
 * @param gameMode  the game mode players enter in: {@link GameType#ADVENTURE} (default) or {@link GameType#SURVIVAL},
 *                  e.g. for mining rooms (US-31)
 * @param playerEffects    mob effects every player gets on entry (US-36)
 * @param playerAttributes attribute modifiers every player gets while inside (US-36)
 */
public record ChallengeStructure(
        Identifier theme,
        int tier,
        Identifier structure,
        Optional<String> name,
        Optional<String> author,
        Optional<Long> created,
        int weight,
        boolean rotation,
        GameType gameMode,
        List<ChallengeEffect> playerEffects,
        List<ChallengeAttribute> playerAttributes
) {

    /** Game modes a challenge may use; Creative and Spectator would bypass the challenge. */
    public static final Codec<GameType> GAME_MODE_CODEC = GameType.CODEC.validate(mode -> mode == GameType.ADVENTURE || mode == GameType.SURVIVAL
            ? DataResult.success(mode) : DataResult.error(() -> "Challenge game mode must be adventure or survival, not " + mode.getName()));

    /** Codec for the metadata JSON file. */
    public static final Codec<ChallengeStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("theme").forGetter(ChallengeStructure::theme),
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(ChallengeStructure::tier),
            Identifier.CODEC.fieldOf("structure").forGetter(ChallengeStructure::structure),
            Codec.STRING.optionalFieldOf("name").forGetter(ChallengeStructure::name),
            Codec.STRING.optionalFieldOf("author").forGetter(ChallengeStructure::author),
            Codec.LONG.optionalFieldOf("created").forGetter(ChallengeStructure::created),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(ChallengeStructure::weight),
            Codec.BOOL.optionalFieldOf("rotation", false).forGetter(ChallengeStructure::rotation),
            GAME_MODE_CODEC.optionalFieldOf("game_mode", GameType.ADVENTURE).forGetter(ChallengeStructure::gameMode),
            LenientCodecs.list(ChallengeEffect.CODEC, "challenge player effect").optionalFieldOf("player_effects", List.of())
                    .forGetter(ChallengeStructure::playerEffects),
            LenientCodecs.list(ChallengeAttribute.CODEC, "challenge player attribute").optionalFieldOf("player_attributes", List.of())
                    .forGetter(ChallengeStructure::playerAttributes)
    ).apply(instance, ChallengeStructure::new));

    /** Creates the metadata, defensively copying the lists. */
    public ChallengeStructure {
        playerEffects = List.copyOf(playerEffects);
        playerAttributes = List.copyOf(playerAttributes);
    }

    /**
     * Creates metadata with the default game mode (Adventure).
     *
     * @param theme     the theme id
     * @param tier      the tier
     * @param structure the template id
     * @param name      optional name
     * @param author    optional author
     * @param created   optional creation timestamp
     * @param weight    the selection weight
     * @param rotation  whether random rotation is allowed
     */
    public ChallengeStructure(Identifier theme, int tier, Identifier structure, Optional<String> name, Optional<String> author,
            Optional<Long> created, int weight, boolean rotation) {
        this(theme, tier, structure, name, author, created, weight, rotation, GameType.ADVENTURE, List.of(), List.of());
    }

    /**
     * Returns a copy with other editor-managed fields; player effects and attributes are kept.
     *
     * @param name     the name
     * @param weight   the selection weight
     * @param rotation whether random rotation is allowed
     * @param gameMode the game mode
     * @return the updated metadata
     */
    public ChallengeStructure with(Optional<String> name, int weight, boolean rotation, GameType gameMode) {
        return new ChallengeStructure(this.theme, this.tier, this.structure, name, this.author, this.created, weight, rotation, gameMode,
                this.playerEffects, this.playerAttributes);
    }
}

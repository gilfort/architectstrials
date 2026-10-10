package com.gilfort.architectstrials.structure;

import java.util.Optional;

import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

/**
 * How a challenge is played (US-41): time limit and who may enter for how long. The challenge defines these
 * values; scrolls only modify them ({@link com.gilfort.architectstrials.scroll.ScrollModifiers}). Stored as flat
 * fields of the structure metadata:
 * <pre>{@code "time_limit": 300, "max_players": 4, "allow_reentry": true, "portal_open_seconds": 120}</pre>
 *
 * @param timeLimitSeconds  the time limit in seconds; empty = the configured default
 * @param maxPlayers        number of distinct players who may enter ({@code 1} = only the scroll user,
 *                          {@code 0} = unlimited)
 * @param allowReentry      whether players who left without completing may enter again while the portal is open
 * @param portalOpenSeconds {@code 0} = the portal closes after the first pass-through; {@code > 0} = open that many
 *                          seconds after becoming active; {@code -1} = open until the time limit expires
 */
public record ChallengeRunSettings(Optional<Integer> timeLimitSeconds, int maxPlayers, boolean allowReentry, int portalOpenSeconds) {

    /** The defaults: configured time limit, solo, no re-entry, portal closes behind the player. */
    public static final ChallengeRunSettings DEFAULT = new ChallengeRunSettings(Optional.empty(), 1, false, 0);

    /** Codec of the flat metadata fields. */
    public static final MapCodec<ChallengeRunSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time_limit").forGetter(ChallengeRunSettings::timeLimitSeconds),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_players", 1).forGetter(ChallengeRunSettings::maxPlayers),
            Codec.BOOL.optionalFieldOf("allow_reentry", false).forGetter(ChallengeRunSettings::allowReentry),
            Codec.intRange(ScrollOptions.OPEN_UNTIL_TIME_LIMIT, Integer.MAX_VALUE).optionalFieldOf("portal_open_seconds", 0)
                    .forGetter(ChallengeRunSettings::portalOpenSeconds)
    ).apply(instance, ChallengeRunSettings::new));

    /**
     * Returns a copy with another time limit.
     *
     * @param seconds the time limit in seconds, or empty for the configured default
     * @return the updated settings
     */
    public ChallengeRunSettings withTimeLimit(Optional<Integer> seconds) {
        return new ChallengeRunSettings(seconds, this.maxPlayers, this.allowReentry, this.portalOpenSeconds);
    }

    /**
     * Returns a copy with another player limit.
     *
     * @param players the maximum number of players ({@code 0} = unlimited)
     * @return the updated settings
     */
    public ChallengeRunSettings withMaxPlayers(int players) {
        return new ChallengeRunSettings(this.timeLimitSeconds, players, this.allowReentry, this.portalOpenSeconds);
    }

    /**
     * Returns a copy with another re-entry rule.
     *
     * @param reentry whether re-entry is allowed
     * @return the updated settings
     */
    public ChallengeRunSettings withAllowReentry(boolean reentry) {
        return new ChallengeRunSettings(this.timeLimitSeconds, this.maxPlayers, reentry, this.portalOpenSeconds);
    }

    /**
     * Returns a copy with another portal open time.
     *
     * @param seconds the portal open time ({@code 0}, {@code > 0} or {@code -1})
     * @return the updated settings
     */
    public ChallengeRunSettings withPortalOpenSeconds(int seconds) {
        return new ChallengeRunSettings(this.timeLimitSeconds, this.maxPlayers, this.allowReentry, seconds);
    }
}

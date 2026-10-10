package com.gilfort.architectstrials.scroll;

import java.util.Optional;

import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.structure.ChallengeRunSettings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * How a scroll changes the run settings of the challenge it opens (US-41), stored as data component
 * {@code architectstrials:modifiers}. The challenge defines the values ({@link ChallengeRunSettings}); the scroll
 * only scales or extends them, so the same scroll works for a two-minute loot room and an hour-long boss room.
 * <pre>{@code "architectstrials:modifiers": {"time": 25, "portal_open": -50, "max_players": 2, "allow_reentry": true}}</pre>
 *
 * @param timePercent       change of the time limit in percent ({@code 25} = ×1.25, {@code -50} = ×0.5); always above −100
 * @param portalOpenPercent change of a positive portal open time in percent; always above −100
 * @param maxPlayers        additional players ({@code n > 0}), {@code 0} = unlimited, empty = unchanged
 * @param allowReentry      {@code true} switches re-entry on; a scroll can never switch it off
 */
public record ScrollModifiers(double timePercent, double portalOpenPercent, Optional<Integer> maxPlayers, boolean allowReentry) {

    /** No changes. */
    public static final ScrollModifiers NONE = new ScrollModifiers(0.0, 0.0, Optional.empty(), false);

    private static final Codec<Double> PERCENT = Codec.DOUBLE.validate(percent -> percent > -100.0
            ? DataResult.success(percent) : DataResult.error(() -> "Percent modifiers must be above -100, not " + percent));

    /** Persistent codec; every field is optional. */
    public static final Codec<ScrollModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PERCENT.optionalFieldOf("time", 0.0).forGetter(ScrollModifiers::timePercent),
            PERCENT.optionalFieldOf("portal_open", 0.0).forGetter(ScrollModifiers::portalOpenPercent),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_players").forGetter(ScrollModifiers::maxPlayers),
            Codec.BOOL.optionalFieldOf("allow_reentry", false).forGetter(ScrollModifiers::allowReentry)
    ).apply(instance, ScrollModifiers::new));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ScrollModifiers> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ScrollModifiers::timePercent,
            ByteBufCodecs.DOUBLE, ScrollModifiers::portalOpenPercent,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), ScrollModifiers::maxPlayers,
            ByteBufCodecs.BOOL, ScrollModifiers::allowReentry,
            ScrollModifiers::new);

    /** @return {@code true} if these modifiers change nothing */
    public boolean isNeutral() {
        return this.equals(NONE);
    }

    /** @return {@code true} if the scroll lets any number of players in */
    public boolean unlimitedPlayers() {
        return this.maxPlayers.isPresent() && this.maxPlayers.get() == 0;
    }

    /**
     * Combines these modifiers with those of an upgrade: percentages multiply ({@code +25 %} twice =
     * {@code +56.25 %}), additional players add up (unlimited wins), re-entry stays on once switched on.
     *
     * @param other the upgrade's modifiers
     * @return the combined modifiers
     */
    public ScrollModifiers combine(ScrollModifiers other) {
        Optional<Integer> players;
        if (this.unlimitedPlayers() || other.unlimitedPlayers()) {
            players = Optional.of(0);
        } else if (this.maxPlayers.isEmpty() && other.maxPlayers.isEmpty()) {
            players = Optional.empty();
        } else {
            players = Optional.of(this.maxPlayers.orElse(0) + other.maxPlayers.orElse(0));
        }
        return new ScrollModifiers(multiply(this.timePercent, other.timePercent), multiply(this.portalOpenPercent, other.portalOpenPercent),
                players, this.allowReentry || other.allowReentry);
    }

    private static double multiply(double first, double second) {
        return ((1.0 + first / 100.0) * (1.0 + second / 100.0) - 1.0) * 100.0;
    }

    /**
     * Returns the time limit of a challenge opened with these modifiers.
     *
     * @param run the challenge's run settings
     * @return the time limit in seconds, at least one
     */
    public int timeLimitSeconds(ChallengeRunSettings run) {
        int base = run.timeLimitSeconds().orElse(ArchitectsTrialsConfig.DEFAULT_TIME_LIMIT_SECONDS.getAsInt());
        return scale(base, this.timePercent);
    }

    /**
     * Returns the admission options of a challenge opened with these modifiers.
     *
     * @param run the challenge's run settings
     * @return the options fixed into the instance
     */
    public ScrollOptions options(ChallengeRunSettings run) {
        int players;
        if (run.maxPlayers() == 0 || this.unlimitedPlayers()) {
            players = 0;
        } else {
            players = run.maxPlayers() + this.maxPlayers.orElse(0);
        }
        int portal = run.portalOpenSeconds() > 0 ? scale(run.portalOpenSeconds(), this.portalOpenPercent) : run.portalOpenSeconds();
        return new ScrollOptions(players, portal, run.allowReentry() || this.allowReentry);
    }

    private static int scale(int seconds, double percent) {
        return (int) Math.max(1L, Math.round(seconds * (1.0 + percent / 100.0)));
    }
}

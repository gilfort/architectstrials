package com.gilfort.architectstrials.scroll;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * Multiplayer options of a challenge scroll, stored as data component {@code architectstrials:options}. They
 * are read when the portal opens and fixed into the instance. All fields are optional; the default is the solo
 * scroll.
 * <pre>{@code "architectstrials:options": {"max_players": 4, "portal_open_seconds": 120, "allow_reentry": true}}</pre>
 *
 * @param maxPlayers        number of distinct players who may enter ({@code 1} = only the scroll user,
 *                          {@code 0} = unlimited); re-entries do not count
 * @param portalOpenSeconds {@code 0} = the portal closes after the first pass-through; {@code > 0} = it stays
 *                          open that many seconds after becoming active; {@code -1} = it stays open until the
 *                          instance's time limit expires. Scrolls for more than one player always stay open at
 *                          least {@value #MIN_MULTIPLAYER_PORTAL_SECONDS} seconds, so everyone can get in
 * @param allowReentry      whether players who left without completing (e.g. died) may enter again while the
 *                          portal is open; players who completed the run can never re-enter
 */
public record ScrollOptions(int maxPlayers, int portalOpenSeconds, boolean allowReentry) {

    /** Value of {@link #portalOpenSeconds()} meaning "open until the time limit expires". */
    public static final int OPEN_UNTIL_TIME_LIMIT = -1;

    /** Minimum portal open duration of scrolls for more than one player, in seconds. */
    public static final int MIN_MULTIPLAYER_PORTAL_SECONDS = 15;

    /** The solo default: one player, portal closes behind them, no re-entry. */
    public static final ScrollOptions DEFAULT = new ScrollOptions(1, 0, false);

    /** Persistent codec. */
    public static final Codec<ScrollOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_players", 1).forGetter(ScrollOptions::maxPlayers),
            Codec.intRange(OPEN_UNTIL_TIME_LIMIT, Integer.MAX_VALUE).optionalFieldOf("portal_open_seconds", 0).forGetter(ScrollOptions::portalOpenSeconds),
            Codec.BOOL.optionalFieldOf("allow_reentry", false).forGetter(ScrollOptions::allowReentry)
    ).apply(instance, ScrollOptions::new));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ScrollOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScrollOptions::maxPlayers,
            ByteBufCodecs.INT, ScrollOptions::portalOpenSeconds,
            ByteBufCodecs.BOOL, ScrollOptions::allowReentry,
            ScrollOptions::new);

    /** Raises the portal open duration of multiplayer scrolls to the minimum. */
    public ScrollOptions {
        if (maxPlayers != 1 && portalOpenSeconds != OPEN_UNTIL_TIME_LIMIT && portalOpenSeconds < MIN_MULTIPLAYER_PORTAL_SECONDS) {
            portalOpenSeconds = MIN_MULTIPLAYER_PORTAL_SECONDS;
        }
    }

    /** @return {@code true} if any number of players may enter */
    public boolean unlimitedPlayers() {
        return this.maxPlayers == 0;
    }

    /** @return {@code true} if only the scroll user may enter */
    public boolean solo() {
        return this.maxPlayers == 1;
    }
}

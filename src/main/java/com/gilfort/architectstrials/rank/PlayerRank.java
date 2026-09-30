package com.gilfort.architectstrials.rank;

import java.util.HashMap;
import java.util.Map;

import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A player's rank per challenge theme. A scroll of tier {@code n} may only be used by a player whose
 * level for the scroll's theme is at least {@code n}.
 * <p>
 * Only explicitly set levels are stored; every other theme uses the configured {@code startingRank}
 * (default 1). Level 0 blocks all scrolls of a theme until the player receives an upgrade, e.g. from a quest or
 * advancement reward. Immutable: changes return a new instance, which keeps attachment syncing reliable.
 *
 * @param levels the explicitly set levels by theme id
 */
public record PlayerRank(Map<Identifier, Integer> levels) {

    /** Persistent codec. */
    public static final MapCodec<PlayerRank> MAP_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .xmap(PlayerRank::new, PlayerRank::levels)
            .fieldOf("levels");

    /** Network codec, used to sync the player's own levels to their client (scroll tooltips). */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerRank> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT)
                    .map(PlayerRank::new, rank -> new HashMap<>(rank.levels()))
                    .cast();

    /** Creates rank data without explicit levels. */
    public PlayerRank() {
        this(Map.of());
    }

    /** Creates rank data, defensively copying the levels. */
    public PlayerRank {
        levels = Map.copyOf(levels);
    }

    /**
     * Returns the level of a theme.
     *
     * @param theme the theme id
     * @return the explicitly set level, or the configured starting rank
     */
    public int level(Identifier theme) {
        return this.levels.getOrDefault(theme, ArchitectsTrialsConfig.STARTING_RANK.getAsInt());
    }

    /**
     * Returns a copy with the level of a theme set; negative values are clamped to 0.
     *
     * @param theme the theme id
     * @param level the new level
     * @return the updated rank data
     */
    public PlayerRank withLevel(Identifier theme, int level) {
        Map<Identifier, Integer> updated = new HashMap<>(this.levels);
        updated.put(theme, Math.max(0, level));
        return new PlayerRank(updated);
    }

    /**
     * Checks whether a scroll tier may be used.
     *
     * @param theme the theme id
     * @param tier  the scroll tier
     * @return {@code true} if the level of the theme is at least the tier
     */
    public boolean allows(Identifier theme, int tier) {
        return this.level(theme) >= tier;
    }
}

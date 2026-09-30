package com.gilfort.architectstrials.difficulty;

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
 * A player's difficulty level per challenge theme. A scroll of tier {@code n} may only be used by a player whose
 * level for the scroll's theme is at least {@code n}.
 * <p>
 * Only explicitly set levels are stored; every other theme uses the configured {@code startingDifficulty}
 * (default 1). Level 0 blocks all scrolls of a theme until the player receives an upgrade, e.g. from a quest or
 * advancement reward. Immutable: changes return a new instance, which keeps attachment syncing reliable.
 *
 * @param levels the explicitly set levels by theme id
 */
public record PlayerDifficulty(Map<Identifier, Integer> levels) {

    /** Persistent codec. */
    public static final MapCodec<PlayerDifficulty> MAP_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT)
            .xmap(PlayerDifficulty::new, PlayerDifficulty::levels)
            .fieldOf("levels");

    /** Network codec, used to sync the player's own levels to their client (scroll tooltips). */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDifficulty> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT)
                    .map(PlayerDifficulty::new, difficulty -> new HashMap<>(difficulty.levels()))
                    .cast();

    /** Creates difficulty data without explicit levels. */
    public PlayerDifficulty() {
        this(Map.of());
    }

    /** Creates difficulty data, defensively copying the levels. */
    public PlayerDifficulty {
        levels = Map.copyOf(levels);
    }

    /**
     * Returns the level of a theme.
     *
     * @param theme the theme id
     * @return the explicitly set level, or the configured starting difficulty
     */
    public int level(Identifier theme) {
        return this.levels.getOrDefault(theme, ArchitectsTrialsConfig.STARTING_DIFFICULTY.getAsInt());
    }

    /**
     * Returns a copy with the level of a theme set; negative values are clamped to 0.
     *
     * @param theme the theme id
     * @param level the new level
     * @return the updated difficulty data
     */
    public PlayerDifficulty withLevel(Identifier theme, int level) {
        Map<Identifier, Integer> updated = new HashMap<>(this.levels);
        updated.put(theme, Math.max(0, level));
        return new PlayerDifficulty(updated);
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

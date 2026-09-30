package com.gilfort.architectstrials.run;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;

/**
 * Per-player count of completed runs, keyed by theme and tier. Persisted as player attachment and used by
 * the run-completed advancement criterion.
 */
public final class RunStatistics {

    /** Codec used to persist the statistics as a player attachment. */
    public static final MapCodec<RunStatistics> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
            .xmap(RunStatistics::new, statistics -> Map.copyOf(statistics.completed))
            .fieldOf("completed");

    private final Map<String, Integer> completed;

    /** Creates empty statistics. */
    public RunStatistics() {
        this(Map.of());
    }

    private RunStatistics(Map<String, Integer> completed) {
        this.completed = new HashMap<>(completed);
    }

    /**
     * Returns how many runs of a theme and tier were completed.
     *
     * @param theme the theme id
     * @param tier  the tier
     * @return the number of completed runs
     */
    public int completed(Identifier theme, int tier) {
        return this.completed.getOrDefault(key(theme, tier), 0);
    }

    /**
     * Returns the total number of completed runs of a theme across all tiers in {@code [minTier, maxTier]}.
     *
     * @param theme   the theme id, or {@code null} for all themes
     * @param minTier the lowest tier (inclusive)
     * @param maxTier the highest tier (inclusive)
     * @return the number of completed runs
     */
    public int completed(Identifier theme, int minTier, int maxTier) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : this.completed.entrySet()) {
            int separator = entry.getKey().lastIndexOf('#');
            Identifier entryTheme = Identifier.parse(entry.getKey().substring(0, separator));
            int entryTier = Integer.parseInt(entry.getKey().substring(separator + 1));
            if ((theme == null || theme.equals(entryTheme)) && entryTier >= minTier && entryTier <= maxTier) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /**
     * Counts one completed run.
     *
     * @param theme the theme id
     * @param tier  the tier
     */
    public void increment(Identifier theme, int tier) {
        this.completed.merge(key(theme, tier), 1, Integer::sum);
    }

    private static String key(Identifier theme, int tier) {
        return theme + "#" + tier;
    }
}

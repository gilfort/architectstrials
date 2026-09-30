package com.gilfort.architectstrials.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;

/**
 * Server-side structure pool: all stored challenge structures, grouped by theme and tier.
 * <p>
 * The available tiers of a theme are derived solely from the structures in the pool.
 */
public final class ChallengeStructures {

    private static Map<Identifier, ChallengeStructure> byId = Map.of();
    private static Map<Identifier, Map<Integer, List<Identifier>>> pools = Map.of();

    private ChallengeStructures() {
    }

    /**
     * Replaces the pool. Called on every datapack (re)load.
     *
     * @param structures all loaded structures by metadata id
     */
    static void set(Map<Identifier, ChallengeStructure> structures) {
        Map<Identifier, Map<Integer, List<Identifier>>> grouped = new HashMap<>();
        structures.forEach((id, structure) -> grouped
                .computeIfAbsent(structure.theme(), theme -> new TreeMap<>())
                .computeIfAbsent(structure.tier(), tier -> new ArrayList<>())
                .add(id));
        byId = Map.copyOf(structures);
        pools = grouped;
        ArchitectsTrials.LOGGER.info("Loaded {} challenge structure(s) for {} theme(s)", byId.size(), pools.size());
    }

    /**
     * Looks up a structure by its metadata id.
     *
     * @param id the metadata id
     * @return the structure, or empty if unknown
     */
    public static Optional<ChallengeStructure> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns the ids of all structures in a theme + tier pool.
     *
     * @param theme the theme id
     * @param tier  the tier
     * @return the metadata ids, empty if the pool is empty
     */
    public static List<Identifier> pool(Identifier theme, int tier) {
        return Collections.unmodifiableList(pools.getOrDefault(theme, Map.of()).getOrDefault(tier, List.of()));
    }

    /**
     * Returns all tiers that have at least one structure for a theme.
     *
     * @param theme the theme id
     * @return the tiers in ascending order
     */
    public static SortedSet<Integer> tiers(Identifier theme) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(pools.getOrDefault(theme, Map.of()).keySet()));
    }

    /**
     * Draws a structure from a pool, weighted by {@link ChallengeStructure#weight()}.
     *
     * @param theme  the theme id
     * @param tier   the tier
     * @param random the random source
     * @return the id of the drawn structure, or empty if the pool is empty
     */
    public static Optional<Identifier> draw(Identifier theme, int tier, RandomSource random) {
        return WeightedRandom.getRandomItem(random, pool(theme, tier), id -> byId.get(id).weight());
    }
}

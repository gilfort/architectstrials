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
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Server-side structure pool: all stored challenge structures, grouped by theme and tier.
 * <p>
 * The available tiers of a theme are derived solely from the structures in the pool.
 */
public final class ChallengeStructures {

    private static Map<Identifier, ChallengeStructure> byId = Map.of();
    private static Map<Identifier, Map<Integer, List<Identifier>>> pools = Map.of();
    private static Map<Identifier, String> sources = Map.of();

    private ChallengeStructures() {
    }

    /**
     * Replaces the pool and the source packs. Called on every datapack (re)load.
     *
     * @param structures  all loaded structures by metadata id
     * @param packSources the datapack id each metadata file comes from (US-40)
     */
    static void set(Map<Identifier, ChallengeStructure> structures, Map<Identifier, String> packSources) {
        sources = Map.copyOf(packSources);
        set(structures);
    }

    /**
     * Replaces the pool, keeping the source packs.
     *
     * @param structures all loaded structures by metadata id
     */
    private static void set(Map<Identifier, ChallengeStructure> structures) {
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
     * Draws a structure like {@link #draw}, skipping structures that could never be entered: a template without
     * a player spawn marker removes its structure from the pool (with a warning) and the draw is repeated.
     * Templates are only loaded when drawn, never all at once on (re)load. Structures whose template cannot be
     * loaded are returned and fail on instance creation instead.
     *
     * @param server the server
     * @param theme  the theme id
     * @param tier   the tier
     * @param random the random source
     * @return the id of the drawn structure, or empty if no enterable structure is left in the pool
     */
    public static Optional<Identifier> drawEnterable(MinecraftServer server, Identifier theme, int tier, RandomSource random) {
        StructurePlaceSettings settings = new StructurePlaceSettings();
        while (true) {
            Optional<Identifier> drawn = draw(theme, tier, random);
            if (drawn.isEmpty()) {
                return drawn;
            }
            Optional<StructureTemplate> template = server.getStructureTemplateManager().get(byId.get(drawn.get()).structure());
            if (template.isEmpty() || !template.get().filterBlocks(BlockPos.ZERO, settings, ModBlocks.PLAYER_SPAWN_MARKER.get()).isEmpty()) {
                return drawn;
            }
            ArchitectsTrials.LOGGER.warn("Challenge structure {} has no player spawn marker and is skipped", drawn.get());
            Map<Identifier, ChallengeStructure> remaining = new HashMap<>(byId);
            remaining.remove(drawn.get());
            set(remaining);
        }
    }

    /**
     * Returns all loaded structures.
     *
     * @return a read-only view of all structures by metadata id
     */
    public static Map<Identifier, ChallengeStructure> all() {
        return byId;
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
     * Returns the datapack a structure's metadata file comes from (US-40).
     *
     * @param id the metadata id
     * @return the pack id (e.g. {@code file/architectstrials_structures}), or empty if unknown
     */
    public static Optional<String> source(Identifier id) {
        return Optional.ofNullable(sources.get(id));
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

package com.gilfort.architectstrials.browser;

import java.util.List;
import java.util.Map;

import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.sub.SubStructure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Everything the challenge browser (US-40) shows: all themes with their challenge structures and all sub
 * structures, each with metadata, source pack, template statistics and validation problems. Built on the server
 * and sent to the client as a whole.
 *
 * @param themes        the themes, sorted by id
 * @param subStructures the sub structures, sorted by id
 */
public record BrowserSnapshot(List<ThemeEntry> themes, List<SubEntry> subStructures) {

    /** An empty snapshot. */
    public static final BrowserSnapshot EMPTY = new BrowserSnapshot(List.of(), List.of());

    private static final Codec<Map<String, Integer>> PROBLEMS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    /** Codec, used for the network transfer. */
    public static final Codec<BrowserSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ThemeEntry.CODEC.listOf().fieldOf("themes").forGetter(BrowserSnapshot::themes),
            SubEntry.CODEC.listOf().fieldOf("sub_structures").forGetter(BrowserSnapshot::subStructures)
    ).apply(instance, BrowserSnapshot::new));

    /** Creates the snapshot, defensively copying the lists. */
    public BrowserSnapshot {
        themes = List.copyOf(themes);
        subStructures = List.copyOf(subStructures);
    }

    /**
     * A theme with its challenge structures.
     *
     * @param id         the theme id
     * @param registered whether the theme is in the theme list (structures may name themes that do not exist)
     * @param challenges the challenge structures, sorted by tier and id
     */
    public record ThemeEntry(Identifier id, boolean registered, List<ChallengeEntry> challenges) {

        /** Codec. */
        public static final Codec<ThemeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ThemeEntry::id),
                Codec.BOOL.fieldOf("registered").forGetter(ThemeEntry::registered),
                ChallengeEntry.CODEC.listOf().fieldOf("challenges").forGetter(ThemeEntry::challenges)
        ).apply(instance, ThemeEntry::new));

        /** Creates the entry, defensively copying the list. */
        public ThemeEntry {
            challenges = List.copyOf(challenges);
        }

        /** @return the number of problems: one per broken challenge, plus one if the theme is not registered */
        public int problemCount() {
            return (int) this.challenges.stream().filter(challenge -> !challenge.problems().isEmpty()).count() + (this.registered ? 0 : 1);
        }
    }

    /**
     * A challenge structure.
     *
     * @param id       the metadata id
     * @param metadata the metadata
     * @param source   the datapack the metadata comes from
     * @param editable whether it lives in the managed datapack and can be changed by the mod
     * @param revision a hash of the metadata, sent back with edits to detect concurrent changes (US-42)
     * @param stats    the template statistics
     * @param problems validation problems, as description → occurrences
     */
    public record ChallengeEntry(Identifier id, ChallengeStructure metadata, String source, boolean editable, String revision,
            StructureStats stats, Map<String, Integer> problems) {

        /** Codec. */
        public static final Codec<ChallengeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ChallengeEntry::id),
                ChallengeStructure.CODEC.fieldOf("metadata").forGetter(ChallengeEntry::metadata),
                Codec.STRING.fieldOf("source").forGetter(ChallengeEntry::source),
                Codec.BOOL.fieldOf("editable").forGetter(ChallengeEntry::editable),
                Codec.STRING.fieldOf("revision").forGetter(ChallengeEntry::revision),
                StructureStats.CODEC.fieldOf("stats").forGetter(ChallengeEntry::stats),
                PROBLEMS_CODEC.fieldOf("problems").forGetter(ChallengeEntry::problems)
        ).apply(instance, ChallengeEntry::new));

        /** Creates the entry, defensively copying the problems. */
        public ChallengeEntry {
            problems = Map.copyOf(problems);
        }
    }

    /**
     * A sub structure.
     *
     * @param id       the sub structure id
     * @param metadata the metadata
     * @param source   the datapack the metadata comes from
     * @param editable whether it lives in the managed datapack and can be changed by the mod
     * @param stats    the template statistics
     * @param problems validation problems, as description → occurrences
     * @param usedBy   metadata ids of the challenge structures whose Sub Structure Markers reference it, sorted
     */
    public record SubEntry(Identifier id, SubStructure metadata, String source, boolean editable, StructureStats stats,
            Map<String, Integer> problems, List<Identifier> usedBy) {

        /** Codec. */
        public static final Codec<SubEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(SubEntry::id),
                SubStructure.CODEC.fieldOf("metadata").forGetter(SubEntry::metadata),
                Codec.STRING.fieldOf("source").forGetter(SubEntry::source),
                Codec.BOOL.fieldOf("editable").forGetter(SubEntry::editable),
                StructureStats.CODEC.fieldOf("stats").forGetter(SubEntry::stats),
                PROBLEMS_CODEC.fieldOf("problems").forGetter(SubEntry::problems),
                Identifier.CODEC.listOf().fieldOf("used_by").forGetter(SubEntry::usedBy)
        ).apply(instance, SubEntry::new));

        /** Creates the entry, defensively copying the collections. */
        public SubEntry {
            problems = Map.copyOf(problems);
            usedBy = List.copyOf(usedBy);
        }
    }
}

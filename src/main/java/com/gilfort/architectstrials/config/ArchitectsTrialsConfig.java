package com.gilfort.architectstrials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration of Architect's Trials ({@code serverconfig/architectstrials-server.toml}, per world).
 * <p>
 * Only global, content-independent options live here. Themes, structures and tiers are defined via
 * datapacks exclusively.
 */
public final class ArchitectsTrialsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Distance in blocks between two challenge slots of the same theme dimension. Only applied to theme
     * dimensions without occupied slots; dimensions with active instances keep their stored spacing.
     */
    public static final ModConfigSpec.IntValue SLOT_SPACING = BUILDER
            .comment("Distance in blocks between two challenge instances in the same theme dimension.",
                    "Only applies to theme dimensions that currently have no instances.")
            .defineInRange("slotSpacing", 2048, 256, 1_000_000);

    /** Maximum number of simultaneously occupied slots per theme dimension; {@code 0} means unlimited. */
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_INSTANCES = BUILDER
            .comment("Maximum number of simultaneous challenge instances per theme dimension. 0 = unlimited.",
                    "Every running instance brings its own mobs and spawners; raise it if your server can handle more.")
            .defineInRange("maxConcurrentInstances", 4, 0, Integer.MAX_VALUE);

    /** Y coordinate the bottom of every challenge structure is placed at. */
    public static final ModConfigSpec.IntValue STRUCTURE_PLACEMENT_Y = BUILDER
            .comment("Y coordinate the bottom of every challenge structure is placed at.")
            .defineInRange("structurePlacementY", 64, -64, 319);

    /** Seconds an opened, ready portal waits for its player before it collapses. */
    public static final ModConfigSpec.IntValue PORTAL_TIMEOUT_SECONDS = BUILDER
            .comment("Seconds an opened, ready challenge portal waits for its player before it collapses.",
                    "The instance is cleaned up and the scroll may drop again (see unusedPortalScrollDropChance).")
            .defineInRange("portalTimeoutSeconds", 60, 1, 3600);

    /** Chance that the scroll drops back when an opened portal collapses unused. */
    public static final ModConfigSpec.DoubleValue UNUSED_PORTAL_SCROLL_DROP_CHANCE = BUILDER
            .comment("Chance (0.0-1.0) that the scroll drops back when an opened portal collapses unused.")
            .defineInRange("unusedPortalScrollDropChance", 0.5, 0.0, 1.0);

    /** Rank every player starts with in every theme; 0 blocks all scrolls until upgraded. */
    public static final ModConfigSpec.IntValue STARTING_RANK = BUILDER
            .comment("Rank every player starts with in every theme. A scroll of tier N needs level N or higher.",
                    "0 blocks all scrolls until the player receives an upgrade (e.g. via /at rank in an advancement reward).")
            .defineInRange("startingRank", 1, 0, 1000);

    /** Time limit of challenges whose scroll defines none, in minutes. */
    public static final ModConfigSpec.IntValue DEFAULT_TIME_LIMIT_MINUTES = BUILDER
            .comment("Time limit in minutes for challenges whose scroll does not define one.",
                    "Players still inside when it expires are sent back (the run does not count as completed).")
            .defineInRange("defaultTimeLimitMinutes", 60, 1, 1440);

    /** Whether failed sub structure generations are written to the log (US-32). */
    public static final ModConfigSpec.BooleanValue LOG_FAILED_SUB_STRUCTURES = BUILDER
            .comment("Write failed sub structure generations to the log (overwritten spawn / exit marker, outside the",
                    "slot area, unknown sub structure). Off by default; useful while building.")
            .define("logFailedSubStructures", false);

    /** The built specification, registered as {@code SERVER} config. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ArchitectsTrialsConfig() {
    }
}

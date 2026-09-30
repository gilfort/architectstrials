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
            .comment("Maximum number of simultaneous challenge instances per theme dimension. 0 = unlimited.")
            .defineInRange("maxConcurrentInstances", 0, 0, Integer.MAX_VALUE);

    /** The built specification, registered as {@code SERVER} config. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ArchitectsTrialsConfig() {
    }
}

package com.gilfort.architectstrials.sub;

import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * All sub structures (US-32) of the loaded datapacks, by id. Filled by the {@link Loader} on every (re)load.
 */
public final class SubStructures {

    private static Map<Identifier, SubStructure> byId = Map.of();

    private SubStructures() {
    }

    /** @return all sub structures by id */
    public static Map<Identifier, SubStructure> all() {
        return byId;
    }

    /**
     * Looks up a sub structure.
     *
     * @param id the sub structure id
     * @return the sub structure, if loaded
     */
    public static Optional<SubStructure> get(Identifier id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Server reload listener loading {@code data/<ns>/architectstrials/sub/**.json}.
     */
    public static final class Loader extends SimpleJsonResourceReloadListener<SubStructure> {

        /** Id under which this listener is registered. */
        public static final Identifier ID = ArchitectsTrials.id("sub_structures");

        /** Creates the loader. */
        public Loader() {
            super(SubStructure.CODEC, FileToIdConverter.json("architectstrials/sub"));
        }

        @Override
        protected void apply(Map<Identifier, SubStructure> structures, ResourceManager manager, ProfilerFiller profiler) {
            byId = Map.copyOf(structures);
            ArchitectsTrials.LOGGER.info("Loaded {} sub structure(s)", byId.size());
        }
    }
}

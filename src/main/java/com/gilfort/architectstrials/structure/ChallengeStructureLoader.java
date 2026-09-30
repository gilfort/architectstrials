package com.gilfort.architectstrials.structure;

import java.util.Map;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Server reload listener that loads all challenge structure metadata files from
 * {@code data/<ns>/architectstrials/challenge/**.json} into {@link ChallengeStructures}.
 */
public final class ChallengeStructureLoader extends SimpleJsonResourceReloadListener<ChallengeStructure> {

    /** Id under which this listener is registered. */
    public static final Identifier ID = ArchitectsTrials.id("challenge_structures");

    /** Creates the loader. */
    public ChallengeStructureLoader() {
        super(ChallengeStructure.CODEC, FileToIdConverter.json("architectstrials/challenge"));
    }

    /**
     * Publishes the loaded structures to the pool.
     *
     * @param structures the loaded structures by metadata id
     * @param manager    the resource manager of the reloading datapacks
     * @param profiler   the reload profiler
     */
    @Override
    protected void apply(Map<Identifier, ChallengeStructure> structures, ResourceManager manager, ProfilerFiller profiler) {
        ChallengeStructures.set(structures);
    }
}

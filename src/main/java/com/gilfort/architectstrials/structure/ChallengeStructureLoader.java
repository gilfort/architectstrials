package com.gilfort.architectstrials.structure;

import java.util.HashMap;
import java.util.Map;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
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

    /** Maps metadata ids to their files below {@code data/<ns>/}. */
    private static final FileToIdConverter LISTER = FileToIdConverter.json("architectstrials/challenge");

    /** Creates the loader. */
    public ChallengeStructureLoader() {
        super(ChallengeStructure.CODEC, LISTER);
    }

    /**
     * Publishes the loaded structures to the pool, together with the datapack each one comes from (US-40).
     *
     * @param structures the loaded structures by metadata id
     * @param manager    the resource manager of the reloading datapacks
     * @param profiler   the reload profiler
     */
    @Override
    protected void apply(Map<Identifier, ChallengeStructure> structures, ResourceManager manager, ProfilerFiller profiler) {
        ChallengeStructures.set(structures, sources(LISTER, structures.keySet(), manager));
    }

    /**
     * Looks up the datapack every loaded file comes from.
     *
     * @param lister  the converter between ids and files
     * @param ids     the loaded ids
     * @param manager the resource manager of the reloading datapacks
     * @return the pack id per loaded id (ids whose file cannot be found are left out)
     */
    public static Map<Identifier, String> sources(FileToIdConverter lister, Iterable<Identifier> ids, ResourceManager manager) {
        Map<Identifier, String> sources = new HashMap<>();
        for (Identifier id : ids) {
            manager.getResource(lister.idToFile(id)).map(Resource::sourcePackId).ifPresent(pack -> sources.put(id, pack));
        }
        return sources;
    }
}

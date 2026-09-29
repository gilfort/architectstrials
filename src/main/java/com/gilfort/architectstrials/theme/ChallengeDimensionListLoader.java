package com.gilfort.architectstrials.theme;

import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Set;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Server reload listener that reads and merges all challenge dimension list files.
 * <p>
 * The merged id set is handed to {@link ChallengeThemes}, which validates it against the dimensions
 * that actually exist on the server.
 *
 * @see ChallengeDimensionList
 */
public final class ChallengeDimensionListLoader extends SimplePreparableReloadListener<Set<Identifier>> {

    /** Id under which this listener is registered. */
    public static final Identifier ID = ArchitectsTrials.id("challenge_dimensions");

    /** Path of the list file inside every datapack namespace. */
    private static final String FILE_PATH = "architectstrials/challenge_dimensions.json";

    /**
     * Collects all dimension ids from every list file in every namespace, honouring {@code replace}.
     *
     * @param manager  the resource manager of the reloading datapacks
     * @param profiler the reload profiler
     * @return the merged, insertion-ordered set of dimension ids
     */
    @Override
    protected Set<Identifier> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Set<Identifier> merged = new LinkedHashSet<>();
        for (String namespace : manager.getNamespaces()) {
            Identifier file = Identifier.fromNamespaceAndPath(namespace, FILE_PATH);
            for (Resource resource : manager.getResourceStack(file)) {
                readInto(file, resource, merged);
            }
        }
        return merged;
    }

    /**
     * Publishes the merged ids to {@link ChallengeThemes}.
     *
     * @param ids      the merged dimension ids
     * @param manager  the resource manager of the reloading datapacks
     * @param profiler the reload profiler
     */
    @Override
    protected void apply(Set<Identifier> ids, ResourceManager manager, ProfilerFiller profiler) {
        ChallengeThemes.setDeclaredDimensions(ids);
    }

    /**
     * Parses one list file and merges it into the accumulated set. Malformed files are logged and skipped.
     *
     * @param file     the file id, used for logging
     * @param resource the resource to read
     * @param merged   the accumulated ids
     */
    private static void readInto(Identifier file, Resource resource, Set<Identifier> merged) {
        try (Reader reader = resource.openAsReader()) {
            ChallengeDimensionList.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .ifError(error -> ArchitectsTrials.LOGGER.error("Invalid challenge dimension list {} in pack {}: {}",
                            file, resource.sourcePackId(), error.message()))
                    .ifSuccess(list -> {
                        if (list.replace()) {
                            merged.clear();
                        }
                        merged.addAll(list.values());
                    });
        } catch (Exception e) {
            ArchitectsTrials.LOGGER.error("Could not read challenge dimension list {} in pack {}", file, resource.sourcePackId(), e);
        }
    }
}

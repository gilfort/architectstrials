package com.gilfort.architectstrials.marker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Registry of marker resolvers and the second pass over a placed structure.
 * <p>
 * Every marker type registers its block together with a {@link MarkerResolver}. After a structure template
 * has been placed, {@link #resolveAll} visits every occurrence of every registered marker block, using the
 * template's own transformation so rotation and mirroring are handled natively.
 */
public final class MarkerResolvers {

    private static final Map<Block, MarkerResolver> RESOLVERS = new LinkedHashMap<>();

    private MarkerResolvers() {
    }

    /**
     * Registers the resolver of a marker block, replacing any previous registration for that block.
     *
     * @param block    the marker block
     * @param resolver the resolver
     */
    public static synchronized void register(Block block, MarkerResolver resolver) {
        RESOLVERS.put(block, resolver);
    }

    /**
     * Returns all registered resolvers.
     *
     * @return a read-only view of the resolvers by marker block
     */
    public static Map<Block, MarkerResolver> all() {
        return Collections.unmodifiableMap(RESOLVERS);
    }

    /**
     * Resolves all markers of a placed structure that are still in the world (a sub structure may have replaced
     * some).
     *
     * @param context  the placement context
     * @param template the placed template
     * @param origin   the placement origin
     * @param settings the placement settings used for the template
     * @return the number of resolved markers
     */
    public static int resolveAll(MarkerContext context, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        int resolved = 0;
        for (Map.Entry<Block, MarkerResolver> entry : RESOLVERS.entrySet()) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, entry.getKey())) {
                // A sub structure (US-32) may have replaced the marker meanwhile.
                if (context.level().getBlockState(info.pos()).is(entry.getKey())) {
                    entry.getValue().resolve(context, info.pos());
                    resolved++;
                }
            }
        }
        return resolved;
    }
}

package com.gilfort.architectstrials.marker;

import net.minecraft.core.BlockPos;

/**
 * Turns a placed marker block into its runtime form (e.g. spawn a mob, place a spawner, record a spawn
 * point). Registered per marker block in {@link MarkerResolvers}.
 */
@FunctionalInterface
public interface MarkerResolver {

    /**
     * Resolves one marker. Called after the whole structure has been placed, so the marker block and its
     * block entity are present at {@code pos} in world coordinates, already rotated and mirrored.
     *
     * @param context the placement context
     * @param pos     the world position of the marker
     */
    void resolve(MarkerContext context, BlockPos pos);
}

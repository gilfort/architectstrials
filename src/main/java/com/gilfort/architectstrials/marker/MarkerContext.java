package com.gilfort.architectstrials.marker;

import com.gilfort.architectstrials.instance.ChallengeInstance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Context handed to {@link MarkerResolver}s during the marker pass of an instance placement.
 *
 * @param level    the theme level the structure was placed in
 * @param instance the instance being created
 * @param random   the random source of this placement
 */
public record MarkerContext(ServerLevel level, ChallengeInstance instance, RandomSource random) {
}

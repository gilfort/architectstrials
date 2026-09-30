package com.gilfort.architectstrials.marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.SpawnPoint;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Context handed to {@link MarkerResolver}s during the marker pass of an instance placement. Resolvers use it
 * to read placement information and to contribute data to the instance being created.
 */
public final class MarkerContext {

    private final ServerLevel level;
    private final ChallengeInstance instance;
    private final RandomSource random;
    private final List<SpawnPoint> spawnPoints = new ArrayList<>();

    /**
     * Creates a context.
     *
     * @param level    the theme level the structure was placed in
     * @param instance the instance being created (without marker-derived data)
     * @param random   the random source of this placement
     */
    public MarkerContext(ServerLevel level, ChallengeInstance instance, RandomSource random) {
        this.level = level;
        this.instance = instance;
        this.random = random;
    }

    /** @return the theme level the structure was placed in */
    public ServerLevel level() {
        return this.level;
    }

    /** @return the instance being created (without marker-derived data) */
    public ChallengeInstance instance() {
        return this.instance;
    }

    /** @return the random source of this placement */
    public RandomSource random() {
        return this.random;
    }

    /**
     * Records a player entry point.
     *
     * @param spawnPoint the spawn point
     */
    public void addSpawnPoint(SpawnPoint spawnPoint) {
        this.spawnPoints.add(spawnPoint);
    }

    /** @return the recorded player entry points */
    public List<SpawnPoint> spawnPoints() {
        return Collections.unmodifiableList(this.spawnPoints);
    }
}

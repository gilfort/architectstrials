package com.gilfort.architectstrials.marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.SpawnPoint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Context handed to {@link MarkerResolver}s during the marker pass of an instance placement. Resolvers use it
 * to read placement information and to contribute data to the instance being created.
 */
public final class MarkerContext {

    private final ServerLevel level;
    private final ChallengeInstance instance;
    private final RandomSource random;
    private final List<SpawnPoint> spawnPoints = new ArrayList<>();
    private final List<BlockPos> exits = new ArrayList<>();
    private final Set<UUID> requiredMobs = new LinkedHashSet<>();
    private Rotation rotation;
    private Mirror mirror;

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
        this.rotation = instance.rotation();
        this.mirror = instance.mirror();
    }

    /**
     * Sets the transformation of the structure whose markers are resolved next: the instance's own for the main
     * structure, the sub structure's for sub structures (US-32).
     *
     * @param rotation the rotation
     * @param mirror   the mirroring
     */
    public void useTransform(Rotation rotation, Mirror mirror) {
        this.rotation = rotation;
        this.mirror = mirror;
    }

    /** @return the rotation of the structure whose markers are being resolved */
    public Rotation rotation() {
        return this.rotation;
    }

    /** @return the mirroring of the structure whose markers are being resolved */
    public Mirror mirror() {
        return this.mirror;
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

    /**
     * Records the base position of a challenge exit.
     *
     * @param pos the exit base position
     */
    public void addExit(BlockPos pos) {
        this.exits.add(pos);
    }

    /**
     * Records a required mob (US-30).
     *
     * @param mob the UUID of the spawned mob
     */
    public void addRequiredMob(UUID mob) {
        this.requiredMobs.add(mob);
    }

    /** @return the UUIDs of the recorded required mobs */
    public Set<UUID> requiredMobs() {
        return Collections.unmodifiableSet(this.requiredMobs);
    }

    /** @return the recorded exit base positions */
    public List<BlockPos> exits() {
        return Collections.unmodifiableList(this.exits);
    }

    /** @return the recorded player entry points */
    public List<SpawnPoint> spawnPoints() {
        return Collections.unmodifiableList(this.spawnPoints);
    }
}

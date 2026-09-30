package com.gilfort.architectstrials.marker;

import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Resolver of the Trial Spawner Marker: replaces the marker by a vanilla trial spawner.
 * <p>
 * Configuration: one spawn potential per filled row (egg entity + marker equipment, weight = egg count), total
 * mobs = sum of all egg counts, simultaneous mobs from the marker, the marker's loot table as the only reward
 * (none if unset). Spawn range, spawn delay, cooldown and the vanilla per-player scaling stay at their
 * defaults. No vault is placed. The spawner can never turn ominous: its ominous config equals the normal one
 * and ominous detection is skipped for it (see {@link #isOminousBlocked}).
 */
public final class TrialSpawnerMarkerResolver {

    /** Key in the trial spawner's persistent block entity data marking it as created from a marker. */
    public static final String NO_OMINOUS_KEY = ArchitectsTrials.MOD_ID + ":no_ominous";

    private TrialSpawnerMarkerResolver() {
    }

    /**
     * Marker resolver: replaces the marker by a configured trial spawner, or removes it if it holds no spawn egg.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolve(MarkerContext context, BlockPos pos) {
        ServerLevel level = context.level();
        if (!(level.getBlockEntity(pos) instanceof TrialSpawnerMarkerBlockEntity marker) || !marker.hasAnyEgg()) {
            ArchitectsTrials.LOGGER.warn("Trial spawner marker at {} in {} has no spawn egg; removed without spawner", pos,
                    level.dimension().identifier());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        TrialSpawnerConfig config = config(level, marker);
        BlockState spawnerState = Blocks.TRIAL_SPAWNER.defaultBlockState();
        level.setBlock(pos, spawnerState, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner)) {
            return;
        }
        TrialSpawner.FullConfig fullConfig = new TrialSpawner.FullConfig(Holder.direct(config), Holder.direct(config),
                TrialSpawner.FullConfig.DEFAULT.targetCooldownLength(), TrialSpawner.FullConfig.DEFAULT.requiredPlayerRange());
        CompoundTag data = new CompoundTag();
        data.store(TrialSpawner.FullConfig.MAP_CODEC, level.registryAccess().createSerializationContext(NbtOps.INSTANCE), fullConfig);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "trial spawner marker " + pos,
                ArchitectsTrials.LOGGER)) {
            spawner.getTrialSpawner().load(TagValueInput.create(reporter, level.registryAccess(), data));
        }
        spawner.getPersistentData().putBoolean(NO_OMINOUS_KEY, true);
        spawner.setChanged();
        level.sendBlockUpdated(pos, spawnerState, spawnerState, Block.UPDATE_ALL);
    }

    /**
     * Checks whether a trial spawner was created from a marker and must therefore never turn ominous.
     *
     * @param level the level
     * @param pos   the trial spawner position
     * @return {@code true} if ominous detection must be skipped
     */
    public static boolean isOminousBlocked(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner
                && spawner.getPersistentData().getBooleanOr(NO_OMINOUS_KEY, false);
    }

    /**
     * Builds the trial spawner configuration of a marker.
     *
     * @param level  the level
     * @param marker the marker
     * @return the configuration
     */
    public static TrialSpawnerConfig config(ServerLevel level, TrialSpawnerMarkerBlockEntity marker) {
        WeightedList.Builder<SpawnData> potentials = WeightedList.builder();
        for (int row = 0; row < marker.rows(); row++) {
            EntityType<?> type = marker.entityType(row);
            if (type != null) {
                CompoundTag entity = SpawnMarkerResolvers.spawnerEntityTag(level, type, marker.egg(row), marker.equipment(row));
                potentials.add(new SpawnData(entity, Optional.empty(), Optional.empty()), marker.egg(row).getCount());
            }
        }
        WeightedList.Builder<ResourceKey<LootTable>> loot = WeightedList.builder();
        marker.lootTableReference().ifPresent(loot::add);
        TrialSpawnerConfig defaults = TrialSpawnerConfig.DEFAULT;
        return new TrialSpawnerConfig(defaults.spawnRange(), marker.totalMobs(), marker.simultaneousMobs(),
                defaults.totalMobsAddedPerPlayer(), defaults.simultaneousMobsAddedPerPlayer(), defaults.ticksBetweenSpawn(),
                potentials.build(), loot.build(), defaults.itemsToDropWhenOminous());
    }
}

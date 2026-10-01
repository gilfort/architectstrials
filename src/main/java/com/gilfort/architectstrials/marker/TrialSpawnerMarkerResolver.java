package com.gilfort.architectstrials.marker;

import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.loot.LootSetups;

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
 * Normal configuration: one spawn potential per filled row of the normal page (egg entity + marker equipment,
 * weight = egg count), total mobs = sum of the page's egg counts, the page's simultaneous mobs, the marker's loot
 * table as the only reward (none if unset). Spawn range, spawn delay, cooldown and the vanilla per-player scaling
 * stay at their defaults. No vault is placed.
 * <p>
 * Ominous: if the marker blocks it (default), the ominous config equals the normal one and ominous detection is
 * skipped for the spawner (see {@link #isOminousBlocked}). If it is allowed, the ominous config is built the same
 * way from the ominous page (falling back to the normal rows if that page is empty), with the ominous reward
 * (falling back to the normal reward) and the vanilla items dropped during ominous waves.
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
        TrialSpawnerConfig config = config(level, marker, false);
        TrialSpawnerConfig ominousConfig = marker.ominousAllowed() ? config(level, marker, true) : config;
        BlockState spawnerState = Blocks.TRIAL_SPAWNER.defaultBlockState();
        level.setBlock(pos, spawnerState, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner)) {
            return;
        }
        TrialSpawner.FullConfig fullConfig = new TrialSpawner.FullConfig(Holder.direct(config), Holder.direct(ominousConfig),
                TrialSpawner.FullConfig.DEFAULT.targetCooldownLength(), TrialSpawner.FullConfig.DEFAULT.requiredPlayerRange());
        CompoundTag data = new CompoundTag();
        data.store(TrialSpawner.FullConfig.MAP_CODEC, level.registryAccess().createSerializationContext(NbtOps.INSTANCE), fullConfig);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "trial spawner marker " + pos,
                ArchitectsTrials.LOGGER)) {
            spawner.getTrialSpawner().load(TagValueInput.create(reporter, level.registryAccess(), data));
        }
        if (!marker.ominousAllowed()) {
            spawner.getPersistentData().putBoolean(NO_OMINOUS_KEY, true);
        }
        LootSetups.write(spawner, LootSetups.SETUP_KEY, marker.lootSetup(false));
        LootSetups.write(spawner, LootSetups.OMINOUS_SETUP_KEY, marker.lootSetup(true));
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
     * Returns the loot table a configuration ejects: {@link LootSetups#PLACEHOLDER} if a loot setup applies (the
     * spawner then ejects the setup, see {@link LootSetups#ejectTrialReward}), otherwise the reward loot table.
     * Ominous: ominous setup → ominous loot table → normal reward.
     */
    private static Optional<ResourceKey<LootTable>> rewardFor(TrialSpawnerMarkerBlockEntity marker, boolean ominous) {
        if (ominous && !marker.lootSetup(true).isEmpty()) {
            return Optional.of(LootSetups.PLACEHOLDER);
        }
        if (ominous && marker.ominousLootTable().isPresent()) {
            return marker.ominousLootTable();
        }
        return marker.lootSetup(false).isEmpty() ? marker.lootTableReference() : Optional.of(LootSetups.PLACEHOLDER);
    }

    /**
     * Builds the normal or ominous trial spawner configuration of a marker. The ominous configuration uses the
     * ominous page, or the normal rows if that page is empty.
     *
     * @param level   the level
     * @param marker  the marker
     * @param ominous {@code true} for the ominous configuration
     * @return the configuration
     */
    public static TrialSpawnerConfig config(ServerLevel level, TrialSpawnerMarkerBlockEntity marker, boolean ominous) {
        boolean ominousRows = ominous && marker.hasEggs(true);
        int firstRow = TrialSpawnerMarkerBlockEntity.firstRow(ominousRows);
        WeightedList.Builder<SpawnData> potentials = WeightedList.builder();
        for (int row = firstRow; row < firstRow + TrialSpawnerMarkerBlockEntity.ROWS; row++) {
            EntityType<?> type = marker.entityType(row);
            if (type != null) {
                CompoundTag entity = SpawnMarkerResolvers.spawnerEntityTag(level, type, marker.egg(row), marker.markerEquipment(row));
                potentials.add(new SpawnData(entity, Optional.empty(), Optional.empty()), marker.egg(row).getCount());
            }
        }
        WeightedList.Builder<ResourceKey<LootTable>> loot = WeightedList.builder();
        rewardFor(marker, ominous).ifPresent(loot::add);
        TrialSpawnerConfig defaults = TrialSpawnerConfig.DEFAULT;
        return new TrialSpawnerConfig(defaults.spawnRange(), marker.totalMobs(ominousRows), marker.simultaneousMobs(ominous),
                defaults.totalMobsAddedPerPlayer(), defaults.simultaneousMobsAddedPerPlayer(), defaults.ticksBetweenSpawn(),
                potentials.build(), loot.build(), defaults.itemsToDropWhenOminous());
    }
}

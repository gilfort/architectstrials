package com.gilfort.architectstrials.browser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.block.VaultMarkerBlockEntity;
import com.gilfort.architectstrials.loot.LootSetups;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.sub.SubStructureMarkerBlockEntity;
import com.gilfort.architectstrials.sub.SubStructureSetup;

import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Computes the {@link StructureStats} of a template (US-40). The raw template data is loaded into a fresh
 * {@link StructureTemplate} (data-fixed like vanilla does), never into the server's template cache, so browsing
 * does not keep every template in memory. Block entities are loaded to read the marker settings with the same
 * code the markers use.
 */
public final class StructureStatistics {

    private StructureStatistics() {
    }

    /**
     * Computes the statistics of a template.
     *
     * @param server the server
     * @param data   the raw template data, or empty if the file was not found
     * @return the statistics; {@link StructureStats#MISSING} without data
     */
    public static StructureStats compute(MinecraftServer server, Optional<CompoundTag> data) {
        if (data.isEmpty()) {
            return StructureStats.MISSING;
        }
        StructureTemplate template = new StructureTemplate();
        CompoundTag tag = data.get().copy();
        int version = NbtUtils.getDataVersion(tag, 500);
        template.load(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, version));
        return compute(server, template);
    }

    /**
     * Computes the statistics of a loaded template.
     *
     * @param server   the server
     * @param template the template
     * @return the statistics
     */
    public static StructureStats compute(MinecraftServer server, StructureTemplate template) {
        Counter counter = new Counter(server);
        if (!template.palettes.isEmpty()) {
            template.palettes.getFirst().blocks().forEach(counter::block);
        }
        Vec3i size = template.getSize();
        List<StructureStats.MobCount> mobs = new ArrayList<>();
        counter.mobs.forEach((key, count) -> mobs.add(new StructureStats.MobCount(key.kind(), key.entity(), count, key.required())));
        List<StructureStats.LootCount> loot = new ArrayList<>();
        counter.loot.forEach((key, count) -> loot.add(new StructureStats.LootCount(key.kind(), key.table(), count)));
        return new StructureStats(true, size.getX(), size.getY(), size.getZ(), counter.playerSpawns, counter.exits, counter.exitsNeedMobs,
                mobs, loot, counter.vaults, List.copyOf(counter.subStructures));
    }

    /** Grouping key of enemies. */
    private record MobKey(StructureStats.MarkerKind kind, Identifier entity, boolean required) {
    }

    /** Grouping key of loot sources. */
    private record LootKey(StructureStats.LootKind kind, Optional<Identifier> table) {
    }

    /** Collects the figures block by block. */
    private static final class Counter {

        private final MinecraftServer server;
        private final Map<MobKey, Integer> mobs = new LinkedHashMap<>();
        private final Map<LootKey, Integer> loot = new LinkedHashMap<>();
        private final TreeSet<Identifier> subStructures = new TreeSet<>();
        private int playerSpawns;
        private int exits;
        private int exitsNeedMobs;
        private int vaults;

        Counter(MinecraftServer server) {
            this.server = server;
        }

        void block(StructureTemplate.StructureBlockInfo info) {
            if (info.state().is(ModBlocks.PLAYER_SPAWN_MARKER.get())) {
                this.playerSpawns++;
            } else if (info.state().is(ModBlocks.EXIT_MARKER.get())) {
                this.exits++;
            } else if (info.state().is(ModBlocks.VAULT_MARKER.get())) {
                this.vaults++;
            }
            if (info.nbt() == null) {
                return;
            }
            BlockEntity blockEntity;
            try {
                blockEntity = BlockEntity.loadStatic(info.pos(), info.state(), info.nbt(), this.server.registryAccess());
            } catch (RuntimeException exception) {
                ArchitectsTrials.LOGGER.debug("Could not load block entity at {} for statistics", info.pos(), exception);
                return;
            }
            switch (blockEntity) {
                case null -> {
                }
                case ExitMarkerBlockEntity exit -> {
                    if (exit.requiresMobs()) {
                        this.exitsNeedMobs++;
                    }
                }
                case SpawnMarkerBlockEntity marker -> this.mobRow(info.state().is(ModBlocks.SPAWNER_MARKER.get())
                        ? StructureStats.MarkerKind.SPAWNER : StructureStats.MarkerKind.DIRECT, marker, 0, marker.required());
                case TrialSpawnerMarkerBlockEntity marker -> {
                    for (int row = 0; row < marker.rows(); row++) {
                        this.mobRow(row >= TrialSpawnerMarkerBlockEntity.firstRow(true)
                                ? StructureStats.MarkerKind.TRIAL_OMINOUS : StructureStats.MarkerKind.TRIAL, marker, row, false);
                    }
                }
                case SubStructureMarkerBlockEntity marker -> {
                    SubStructureSetup setup = marker.setup();
                    setup.entries().forEach(entry -> this.subStructures.add(entry.structure()));
                    setup.fallback().ifPresent(this.subStructures::add);
                }
                case MobMarkerBlockEntity ignored -> {
                }
                case VaultMarkerBlockEntity ignored -> {
                }
                default -> this.container(blockEntity);
            }
        }

        private void mobRow(StructureStats.MarkerKind kind, MobMarkerBlockEntity marker, int row, boolean required) {
            EntityType<?> type = marker.entityType(row);
            if (type != null) {
                this.mobs.merge(new MobKey(kind, BuiltInRegistries.ENTITY_TYPE.getKey(type), required), marker.egg(row).getCount(), Integer::sum);
            }
        }

        private void container(BlockEntity blockEntity) {
            if (blockEntity instanceof RandomizableContainer lootable && lootable.getLootTable() != null) {
                ResourceKey<LootTable> table = lootable.getLootTable();
                LootKey key = LootSetups.PLACEHOLDER.equals(table)
                        ? new LootKey(StructureStats.LootKind.SETUP, Optional.empty())
                        : new LootKey(StructureStats.LootKind.TABLE, Optional.of(table.identifier()));
                this.loot.merge(key, 1, Integer::sum);
            } else if (blockEntity instanceof Container container && !container.isEmpty()) {
                this.loot.merge(new LootKey(StructureStats.LootKind.FILLED, Optional.empty()), 1, Integer::sum);
            }
        }
    }
}

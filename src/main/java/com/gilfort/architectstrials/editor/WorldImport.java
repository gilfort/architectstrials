package com.gilfort.architectstrials.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.marker.MarkerEquipment;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Copies an area of the world into the editor (US-20).
 * <p>
 * The area is copied 1:1 like a saved structure (blocks, block entities incl. loot tables, decoration entities,
 * no mobs) and placed into the empty editor like {@code editor load}. Afterwards vanilla spawners become Spawner
 * Markers and trial spawners become Trial Spawner Markers (incl. their ominous page), so the builder can adjust
 * them like any marker.
 */
public final class WorldImport {

    /** Above this many non-air blocks, an import has to be confirmed. */
    public static final int CONFIRM_THRESHOLD = 100_000;

    private WorldImport() {
    }

    /**
     * Outcome of an import.
     *
     * @param placed       the box the area was placed into in the editor
     * @param copiedBlocks the number of non-air blocks copied
     * @param spawners     the number of spawners and trial spawners converted to markers
     * @param missingEggs  entity types without a spawn egg item (their marker rows stay empty)
     */
    public record Result(BoundingBox placed, int copiedBlocks, int spawners, List<Identifier> missingEggs) {
    }

    /**
     * Copies a box of a level into the (empty) editor and converts its spawners to markers.
     *
     * @param source the level to copy from
     * @param box    the box to copy
     * @param editor the editor level
     * @return the result
     */
    public static Result importInto(ServerLevel source, BoundingBox box, ServerLevel editor) {
        int blocks = EditorCapture.countBlocks(source, box);
        BoundingBox placed = EditorCapture.place(editor, EditorCapture.copy(source, box));
        List<Identifier> missing = new ArrayList<>();
        int spawners = 0;
        for (BlockEntity blockEntity : blockEntities(editor, placed)) {
            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                convertSpawner(editor, spawner, missing);
                spawners++;
            } else if (blockEntity instanceof TrialSpawnerBlockEntity trialSpawner) {
                convertTrialSpawner(editor, trialSpawner, missing);
                spawners++;
            }
        }
        return new Result(placed, blocks, spawners, missing.stream().distinct().toList());
    }

    private static List<BlockEntity> blockEntities(ServerLevel level, BoundingBox box) {
        List<BlockEntity> result = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX()); chunkX <= SectionPos.blockToSectionCoord(box.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ()); chunkZ <= SectionPos.blockToSectionCoord(box.maxZ()); chunkZ++) {
                level.getChunk(chunkX, chunkZ).getBlockEntities().values().stream()
                        .filter(blockEntity -> box.isInside(blockEntity.getBlockPos()))
                        .forEach(result::add);
            }
        }
        return result;
    }

    /**
     * Replaces a spawner by a Spawner Marker: spawn egg of the spawned entity, stack size = spawn count,
     * equipment from the spawn data.
     */
    private static void convertSpawner(ServerLevel level, SpawnerBlockEntity spawner, List<Identifier> missing) {
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            spawner.getSpawner().save(output);
            data = output.buildResult();
        }
        Optional<SpawnData> spawnData = data.read("SpawnData", SpawnData.CODEC);
        CompoundTag entity = spawnData.map(SpawnData::entityToSpawn).orElseGet(CompoundTag::new);
        Optional<ResourceKey<LootTable>> table = spawnData.flatMap(SpawnData::getEquipment).map(EquipmentTable::lootTable);
        int count = data.getShortOr("SpawnCount", (short) 4);
        BlockPos pos = spawner.getBlockPos();
        MobMarkerBlockEntity marker = replace(level, pos, ModBlocks.SPAWNER_MARKER.get());
        if (marker != null) {
            fillRow(level, marker, 0, entity, table, count, missing);
        }
    }

    /**
     * Replaces a trial spawner by a Trial Spawner Marker. The normal config fills the normal page, a differing
     * ominous config the ominous page (ominous stays blocked until the builder allows it): per page up to three
     * spawn potentials (highest weights first) as rows with egg counts distributed by weight so they sum up to the
     * config's total mobs, simultaneous mobs and the highest-weighted reward loot table.
     */
    private static void convertTrialSpawner(ServerLevel level, TrialSpawnerBlockEntity trialSpawner, List<Identifier> missing) {
        TrialSpawnerConfig normal = trialSpawner.getTrialSpawner().normalConfig();
        TrialSpawnerConfig ominous = trialSpawner.getTrialSpawner().ominousConfig();
        BlockPos pos = trialSpawner.getBlockPos();
        if (!(replace(level, pos, ModBlocks.TRIAL_SPAWNER_MARKER.get()) instanceof TrialSpawnerMarkerBlockEntity marker)) {
            return;
        }
        fillPage(level, marker, normal, false, missing);
        if (!ominous.equals(normal)) {
            fillPage(level, marker, ominous, true, missing);
        }
    }

    private static void fillPage(ServerLevel level, TrialSpawnerMarkerBlockEntity marker, TrialSpawnerConfig config, boolean ominous,
            List<Identifier> missing) {
        List<Weighted<SpawnData>> potentials = config.spawnPotentialsDefinition().unwrap().stream()
                .sorted(Comparator.comparingInt((Weighted<SpawnData> entry) -> entry.weight()).reversed())
                .limit(TrialSpawnerMarkerBlockEntity.ROWS)
                .toList();
        int total = Math.max(1, Math.round(config.totalMobs()));
        int weightSum = potentials.stream().mapToInt(Weighted::weight).sum();
        int firstRow = TrialSpawnerMarkerBlockEntity.firstRow(ominous);
        for (int row = 0; row < potentials.size(); row++) {
            Weighted<SpawnData> potential = potentials.get(row);
            int count = Math.max(1, Math.round((float) total * potential.weight() / Math.max(1, weightSum)));
            fillRow(level, marker, firstRow + row, potential.value().entityToSpawn(),
                    potential.value().getEquipment().map(EquipmentTable::lootTable), count, missing);
        }
        marker.setSimultaneousMobs(ominous, Math.round(config.simultaneousMobs()));
        Optional<ResourceKey<LootTable>> reward = config.lootTablesToEject().unwrap().stream()
                .max(Comparator.comparingInt(Weighted::weight)).map(Weighted::value);
        if (ominous) {
            marker.setOminousLootTable(reward);
        } else {
            marker.setLootTableReference(reward);
        }
    }

    private static MobMarkerBlockEntity replace(ServerLevel level, BlockPos pos, Block markerBlock) {
        level.setBlock(pos, markerBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
        return level.getBlockEntity(pos) instanceof MobMarkerBlockEntity marker ? marker : null;
    }

    /**
     * Fills one marker row from a spawner entity tag: the entity's spawn egg (count capped to the stack size), its
     * configured equipment (fixed items and weighted lists) and the spawn data's equipment loot table. Entities
     * without a spawn egg are reported and leave the row empty.
     */
    private static void fillRow(ServerLevel level, MobMarkerBlockEntity marker, int row, CompoundTag entity,
            Optional<ResourceKey<LootTable>> table, int count, List<Identifier> missing) {
        Identifier id = Identifier.tryParse(entity.getStringOr("id", ""));
        Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        Optional<Holder<Item>> egg = type.flatMap(SpawnEggItem::byId);
        if (egg.isEmpty()) {
            if (id != null) {
                missing.add(id);
            }
            return;
        }
        ItemStack eggs = new ItemStack(egg.get());
        eggs.setCount(Mth.clamp(count, 1, eggs.getMaxStackSize()));
        marker.setItem(row * MarkerSlot.ROW_SIZE, eggs);
        MarkerEquipment equipment = SpawnMarkerResolvers.readEquipment(level, entity);
        equipment.fixed().forEach((slot, stack) -> {
            if (MarkerSlot.EQUIPMENT_SLOTS.contains(slot)) {
                marker.setItem(MarkerSlot.indexOf(row, slot), stack.copyWithCount(1));
            }
        });
        equipment.lists().forEach((slot, list) -> {
            if (MarkerSlot.EQUIPMENT_SLOTS.contains(slot)) {
                marker.setEquipmentList(MarkerSlot.indexOf(row, slot), list);
            }
        });
        marker.setEquipmentTable(row, table.or(equipment::table));
        marker.setChanged();
    }
}

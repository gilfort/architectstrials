package com.gilfort.architectstrials.marker;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Resolvers of the Direct Spawn Marker and the Spawner Marker, plus the equipment handling of mobs spawned
 * from them.
 * <p>
 * Mobs always go through their normal spawn initialization first (e.g. a skeleton gets its bow); every
 * equipment slot filled in the marker then replaces the natural item. All equipment drop chances are 0 %.
 * Spawners carry the marker equipment in the entity's persistent data; it is applied when the spawner
 * finalizes a spawned mob (see {@link #onFinalizeSpawn}).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SpawnMarkerResolvers {

    /** Key in an entity's persistent data holding the marker equipment to apply on spawn. */
    public static final String EQUIPMENT_KEY = ArchitectsTrials.MOD_ID + ":marker_equipment";

    private static final Codec<Map<EquipmentSlot, ItemStack>> EQUIPMENT_CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC);

    private static final double SPREAD_RADIUS = 1.5;

    private static final int SPREAD_ATTEMPTS = 16;

    private SpawnMarkerResolvers() {
    }

    /**
     * Marker resolver of the Direct Spawn Marker: removes the marker and spawns one persistent entity per spawn
     * egg in its slot, equipped as configured.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolveDirect(MarkerContext context, BlockPos pos) {
        ServerLevel level = context.level();
        Optional<Marker> marker = read(level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (marker.isEmpty()) {
            return;
        }
        EntityType<?> type = marker.get().type();
        for (int i = 0; i < marker.get().egg().getCount(); i++) {
            Entity entity = type.spawn(level, marker.get().egg(), null, pos, EntitySpawnReason.STRUCTURE, false, false);
            if (entity == null) {
                continue;
            }
            Vec3 spot = i == 0 ? Vec3.atBottomCenterOf(pos) : spreadPosition(level, type, pos, context.random());
            entity.snapTo(spot.x, spot.y, spot.z, entity.getYRot(), entity.getXRot());
            if (entity instanceof Mob mob) {
                equip(mob, marker.get().equipment());
                mob.setPersistenceRequired();
            }
        }
    }

    /**
     * Finds a spawn position near a marker for additional mobs, so they do not stand exactly inside each other
     * (entities at identical positions never push apart and look like a single mob). Tries random spots within
     * {@value #SPREAD_RADIUS} blocks on the marker's height that are free and have ground below; falls back to a
     * slight offset inside the marker block.
     */
    private static Vec3 spreadPosition(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random) {
        for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() * 2.0 - 1.0) * SPREAD_RADIUS;
            double z = pos.getZ() + 0.5 + (random.nextDouble() * 2.0 - 1.0) * SPREAD_RADIUS;
            BlockPos below = BlockPos.containing(x, pos.getY() - 1, z);
            if (level.noCollision(type.getSpawnAABB(x, pos.getY(), z))
                    && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(x, pos.getY(), z);
            }
        }
        return new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY(),
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5);
    }

    /**
     * Marker resolver of the Spawner Marker: replaces the marker by a vanilla monster spawner that spawns the
     * egg's entity (including the egg's entity data) with the configured equipment; the spawn count per cycle
     * is the egg count. All other spawner settings stay at their vanilla defaults.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolveSpawner(MarkerContext context, BlockPos pos) {
        ServerLevel level = context.level();
        Optional<Marker> marker = read(level, pos);
        if (marker.isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        BlockState spawnerState = Blocks.SPAWNER.defaultBlockState();
        level.setBlock(pos, spawnerState, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return;
        }
        ItemStack egg = marker.get().egg();
        CompoundTag entity = spawnerEntityTag(level, marker.get().type(), egg, marker.get().equipment());

        CompoundTag spawnerData = new CompoundTag();
        spawnerData.store(BaseSpawner.SPAWN_DATA_TAG, SpawnData.CODEC, new SpawnData(entity, Optional.empty(), Optional.empty()));
        spawnerData.putShort("SpawnCount", (short) Math.min(egg.getCount(), Short.MAX_VALUE));
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "spawner marker " + pos, ArchitectsTrials.LOGGER)) {
            spawner.getSpawner().load(level, pos, TagValueInput.create(reporter, level.registryAccess(), spawnerData));
        }
        spawner.setChanged();
        level.sendBlockUpdated(pos, spawnerState, spawnerState, Block.UPDATE_ALL);
    }

    /**
     * Builds the entity tag a spawner uses for a marker mob: the egg's entity data, the entity id and the marker
     * equipment in the entity's persistent data (applied by {@link #onFinalizeSpawn}).
     *
     * @param level     the level (for registry access)
     * @param type      the entity type
     * @param egg       the spawn egg stack
     * @param equipment the marker equipment by slot
     * @return the entity tag
     */
    public static CompoundTag spawnerEntityTag(ServerLevel level, EntityType<?> type, ItemStack egg, Map<EquipmentSlot, ItemStack> equipment) {
        TypedEntityData<EntityType<?>> eggData = egg.get(DataComponents.ENTITY_DATA);
        CompoundTag entity = eggData != null ? eggData.copyTagWithoutId() : new CompoundTag();
        entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        CompoundTag persistent = entity.getCompoundOrEmpty("NeoForgeData");
        persistent.store(EQUIPMENT_KEY, EQUIPMENT_CODEC, ops(level.registryAccess()), equipment);
        entity.put("NeoForgeData", persistent);
        return entity;
    }

    /**
     * Reads the equipment configured in a spawner's entity tag: the marker equipment if the spawner was created
     * from a marker, otherwise the vanilla {@code equipment} field.
     *
     * @param level  the level (for registry access)
     * @param entity the entity tag of the spawn data
     * @return the equipment by slot (possibly empty)
     */
    public static Map<EquipmentSlot, ItemStack> readEquipment(ServerLevel level, CompoundTag entity) {
        RegistryOps<Tag> ops = ops(level.registryAccess());
        CompoundTag persistent = entity.getCompoundOrEmpty("NeoForgeData");
        if (persistent.contains(EQUIPMENT_KEY)) {
            return persistent.read(EQUIPMENT_KEY, EQUIPMENT_CODEC, ops).orElse(Map.of());
        }
        return entity.read("equipment", EQUIPMENT_CODEC, ops).orElse(Map.of());
    }

    /**
     * Applies marker equipment to mobs spawned by a spawner created from a Spawner Marker. The spawner does not
     * run the mob's spawn initialization for configured entities, so it is run here before the equipment is
     * applied.
     *
     * @param event the finalize spawn event
     */
    @SubscribeEvent
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(EQUIPMENT_KEY)) {
            return;
        }
        Map<EquipmentSlot, ItemStack> equipment = persistent.read(EQUIPMENT_KEY, EQUIPMENT_CODEC, ops(mob.registryAccess())).orElse(Map.of());
        persistent.remove(EQUIPMENT_KEY);
        if (!event.isSpawnCancelled()) {
            mob.finalizeSpawn(event.getLevel(), event.getDifficulty(), event.getSpawnType(), event.getSpawnData());
        }
        equip(mob, equipment);
    }

    /**
     * Equips a mob: every given slot replaces the mob's item, and all drop chances are set to 0 %.
     *
     * @param mob       the mob
     * @param equipment the equipment by slot
     */
    public static void equip(Mob mob, Map<EquipmentSlot, ItemStack> equipment) {
        equipment.forEach((slot, stack) -> mob.setItemSlot(slot, stack.copy()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0F);
        }
    }

    /**
     * Reads a spawn marker; logs and returns empty if it holds no spawn egg.
     */
    private static Optional<Marker> read(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker && marker.entityType() != null) {
            return Optional.of(new Marker(marker.entityType(), marker.egg().copy(), new EnumMap<>(marker.equipment())));
        }
        ArchitectsTrials.LOGGER.warn("Spawn marker at {} in {} has no spawn egg; removed without spawning", pos, level.dimension().identifier());
        return Optional.empty();
    }

    private static RegistryOps<Tag> ops(HolderLookup.Provider registries) {
        return registries.createSerializationContext(NbtOps.INSTANCE);
    }

    /**
     * Data read from a spawn marker before it is replaced.
     *
     * @param type      the entity type
     * @param egg       a copy of the spawn egg stack
     * @param equipment the equipment by slot
     */
    private record Marker(EntityType<?> type, ItemStack egg, Map<EquipmentSlot, ItemStack> equipment) {
    }
}

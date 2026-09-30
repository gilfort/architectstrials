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
        for (int i = 0; i < marker.get().egg().getCount(); i++) {
            Entity entity = marker.get().type().spawn(level, marker.get().egg(), null, pos, EntitySpawnReason.STRUCTURE, false, false);
            if (entity instanceof Mob mob) {
                equip(mob, marker.get().equipment());
                mob.setPersistenceRequired();
            }
        }
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
        TypedEntityData<EntityType<?>> eggData = egg.get(DataComponents.ENTITY_DATA);
        CompoundTag entity = eggData != null ? eggData.copyTagWithoutId() : new CompoundTag();
        entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(marker.get().type()).toString());
        CompoundTag persistent = entity.getCompoundOrEmpty("NeoForgeData");
        persistent.store(EQUIPMENT_KEY, EQUIPMENT_CODEC, ops(level.registryAccess()), marker.get().equipment());
        entity.put("NeoForgeData", persistent);

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

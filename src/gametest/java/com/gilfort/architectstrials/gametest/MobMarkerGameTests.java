package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-15 (direct spawn and spawner markers).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class MobMarkerGameTests {

    private MobMarkerGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "mob_marker_direct_spawn", MobMarkerGameTests::directSpawn);
            register(helper, "mob_marker_spawner", MobMarkerGameTests::spawner);
            register(helper, "mob_marker_structure_roundtrip", MobMarkerGameTests::structureRoundtrip);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The direct spawn marker is removed and spawns one persistent mob per egg; filled equipment slots replace
     * the natural equipment, empty ones keep it (skeleton bow), and nothing can drop.
     */
    private static void directSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        BlockPos zombieMarker = new BlockPos(2, 2, 2);
        BlockPos skeletonMarker = new BlockPos(6, 2, 6);
        BlockPos emptyMarker = new BlockPos(4, 2, 4);
        fill(helper, ModBlocks.DIRECT_SPAWN_MARKER.get(), zombieMarker, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 3),
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_SWORD));
        fill(helper, ModBlocks.DIRECT_SPAWN_MARKER.get(), skeletonMarker, new ItemStack(Items.SKELETON_SPAWN_EGG), ItemStack.EMPTY, ItemStack.EMPTY);
        fill(helper, ModBlocks.DIRECT_SPAWN_MARKER.get(), emptyMarker, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        MarkerContext context = context(level, helper.absolutePos(BlockPos.ZERO));
        SpawnMarkerResolvers.resolveDirect(context, helper.absolutePos(zombieMarker));
        SpawnMarkerResolvers.resolveDirect(context, helper.absolutePos(skeletonMarker));
        SpawnMarkerResolvers.resolveDirect(context, helper.absolutePos(emptyMarker));

        for (BlockPos marker : List.of(zombieMarker, skeletonMarker, emptyMarker)) {
            helper.assertBlockPresent(Blocks.AIR, marker);
        }
        AABB area = AABB.of(BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(9, 6, 9))));
        List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, area);
        List<Skeleton> skeletons = level.getEntitiesOfClass(Skeleton.class, area);
        helper.assertTrue(zombies.size() == 3, "Expected 3 zombies, got " + zombies.size());
        helper.assertTrue(skeletons.size() == 1, "Expected 1 skeleton, got " + skeletons.size());
        for (Zombie zombie : zombies) {
            helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Zombie has no iron helmet");
            helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "Zombie has no iron sword");
            helper.assertTrue(zombie.isPersistenceRequired(), "Directly spawned zombie is not persistent");
            assertNoDrops(helper, zombie);
        }
        Skeleton skeleton = skeletons.getFirst();
        helper.assertTrue(skeleton.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "Skeleton lost its natural bow");
        assertNoDrops(helper, skeleton);
        zombies.forEach(Mob::discard);
        skeletons.forEach(Mob::discard);
        helper.succeed();
    }

    /**
     * The spawner marker becomes a vanilla spawner with the egg's entity and the egg count as spawn count; mobs
     * it spawns get their natural setup plus the marker equipment, with 0 % drop chance.
     */
    private static void spawner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        BlockPos marker = new BlockPos(4, 2, 4);
        fill(helper, ModBlocks.SPAWNER_MARKER.get(), marker, new ItemStack(Items.SKELETON_SPAWN_EGG, 2),
                new ItemStack(Items.GOLDEN_HELMET), ItemStack.EMPTY);
        SpawnMarkerResolvers.resolveSpawner(context(level, helper.absolutePos(BlockPos.ZERO)), helper.absolutePos(marker));

        helper.assertBlockPresent(Blocks.SPAWNER, marker);
        SpawnerBlockEntity spawner = helper.getBlockEntity(marker, SpawnerBlockEntity.class);
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            spawner.getSpawner().save(output);
            data = output.buildResult();
        }
        helper.assertTrue(data.getShortOr("SpawnCount", (short) 0) == 2, "Spawn count is not the egg count: " + data);
        CompoundTag entity = data.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity");
        helper.assertTrue(entity.getStringOr("id", "").equals("minecraft:skeleton"), "Spawner entity is not a skeleton: " + entity);

        Mob mob;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            ValueInput input = TagValueInput.create(reporter, level.registryAccess(), entity);
            mob = (Mob) EntityType.loadEntityRecursive(input, level, EntitySpawnReason.SPAWNER, e -> e);
        }
        helper.assertTrue(mob != null, "Spawner entity could not be loaded");
        mob.setPos(helper.absoluteVec(new Vec3(4.5, 2, 6.5)));
        EventHooks.finalizeMobSpawnSpawner(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWNER, null,
                spawner.getSpawner(), false);
        helper.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "Spawned skeleton has no golden helmet");
        helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "Spawned skeleton has no natural bow");
        helper.assertFalse(mob.getPersistentData().contains(SpawnMarkerResolvers.EQUIPMENT_KEY), "Marker equipment was not consumed");
        assertNoDrops(helper, mob);
        helper.succeed();
    }

    /**
     * Mob marker contents survive capturing into a structure template; placing the template rotated moves only
     * the marker position. Markers without a spawn egg produce a save warning.
     */
    private static void structureRoundtrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        helper.setBlock(1, 2, 1, ModBlocks.PLAYER_SPAWN_MARKER.get());
        helper.setBlock(1, 2, 3, ModBlocks.EXIT_MARKER.get());
        fill(helper, ModBlocks.DIRECT_SPAWN_MARKER.get(), new BlockPos(3, 2, 1), new ItemStack(Items.ZOMBIE_SPAWN_EGG, 2),
                new ItemStack(Items.DIAMOND_HELMET), ItemStack.EMPTY);
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(3, 2, 3)));
        EditorCapture.Captured captured = EditorCapture.capture(level, area).orElseThrow();
        helper.assertTrue(EditorCapture.validate(level, captured).warnings().isEmpty(), "Filled mob marker produced a warning");

        fill(helper, ModBlocks.SPAWNER_MARKER.get(), new BlockPos(3, 2, 3), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        EditorCapture.Validation withEmpty = EditorCapture.validate(level, EditorCapture.capture(level, area).orElseThrow());
        helper.assertTrue(withEmpty.valid() && withEmpty.warnings().size() == 1, "Empty mob marker did not produce exactly one warning");

        StructureTemplate template = captured.template();
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        BlockPos origin = helper.absolutePos(new BlockPos(6, 2, 2));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90).setMirror(Mirror.NONE);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        MarkerContext context = context(level, origin);
        MarkerResolvers.resolveAll(context, template, origin, settings);

        BlockPos expected = StructureTemplate.calculateRelativePosition(settings, new BlockPos(2, 0, 0)).offset(origin);
        List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, new AABB(expected).inflate(0.5));
        helper.assertTrue(zombies.size() == 2, "Expected 2 zombies at the rotated marker position " + expected + ", got " + zombies.size());
        helper.assertTrue(zombies.stream().allMatch(zombie -> zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)),
                "Marker equipment did not survive the structure round trip");
        zombies.forEach(Mob::discard);
        helper.succeed();
    }

    private static void prepareFloor(GameTestHelper helper) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 6; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static void fill(GameTestHelper helper, Block block, BlockPos pos, ItemStack egg, ItemStack head, ItemStack mainHand) {
        helper.setBlock(pos, block);
        SpawnMarkerBlockEntity marker = helper.getBlockEntity(pos, SpawnMarkerBlockEntity.class);
        marker.setItem(SpawnMarkerBlockEntity.EGG_SLOT, egg);
        marker.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD), head);
        marker.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.MAINHAND), mainHand);
    }

    private static MarkerContext context(ServerLevel level, BlockPos origin) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, origin, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }

    private static void assertNoDrops(GameTestHelper helper, Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            helper.assertTrue(mob.getDropChances().byEquipment(slot) == 0.0F, "Drop chance of " + slot + " is not 0");
        }
    }
}

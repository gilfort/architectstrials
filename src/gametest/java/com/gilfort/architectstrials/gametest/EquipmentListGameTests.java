package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.editor.WorldImport;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-24 (weighted random equipment per slot).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class EquipmentListGameTests {

    private static final ResourceKey<LootTable> DIAMOND_CHEST = ResourceKey.create(Registries.LOOT_TABLE,
            ArchitectsTrials.id("gametest/equipment_diamond_chest"));

    private EquipmentListGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "equipment_list_rolls", EquipmentListGameTests::rolls);
            register(helper, "equipment_list_direct_spawn", EquipmentListGameTests::directSpawn);
            register(helper, "equipment_list_spawner_and_table", EquipmentListGameTests::spawnerAndTable);
            register(helper, "equipment_list_import_table", EquipmentListGameTests::importTable);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Rolls follow the chances with the remainder as "nothing"; chances are clamped to 100 % in total; the list
     * container stores items and keeps fixed items out of slots with a list.
     */
    private static void rolls(GameTestHelper helper) {
        EquipmentList list = EquipmentList.EMPTY.with(0, new ItemStack(Items.STONE_SWORD), 300).with(1, new ItemStack(Items.BOW), 200);
        EquipmentList clamped = list.with(2, new ItemStack(Items.FISHING_ROD), 800);
        helper.assertTrue(clamped.at(2).orElseThrow().chance() == 500 && clamped.totalChance() == 1000,
                "Chance was not clamped to 100 % in total: " + clamped);
        RandomSource random = RandomSource.create(42L);
        int swords = 0;
        int bows = 0;
        int nothing = 0;
        int rolls = 10_000;
        for (int i = 0; i < rolls; i++) {
            ItemStack result = list.roll(random);
            if (result.is(Items.STONE_SWORD)) {
                swords++;
            } else if (result.is(Items.BOW)) {
                bows++;
            } else if (result.isEmpty()) {
                nothing++;
            }
        }
        helper.assertTrue(Math.abs(swords - 3000) < 300 && Math.abs(bows - 2000) < 300 && Math.abs(nothing - 5000) < 300,
                "Distribution off: swords " + swords + ", bows " + bows + ", nothing " + nothing);

        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.DIRECT_SPAWN_MARKER.get());
        SpawnMarkerBlockEntity marker = helper.getBlockEntity(pos, SpawnMarkerBlockEntity.class);
        int head = SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD);
        Container container = marker.listContainer(head);
        container.setItem(0, new ItemStack(Items.GOLDEN_HELMET));
        helper.assertTrue(marker.equipmentList(head).at(0).map(EquipmentList.Entry::chance).orElse(-1) == 100,
                "New list entry did not get the default chance of 10 %");
        marker.listChances(head).set(0, 2000);
        helper.assertTrue(marker.equipmentList(head).totalChance() == 1000, "Chance above 100 % was not clamped");
        container.removeItemNoUpdate(0);
        helper.assertTrue(marker.equipmentList(head).isEmpty(), "Emptying the list did not remove the entry");
        helper.succeed();
    }

    /**
     * Directly spawned mobs roll their list per mob: about half get the helmet, the others an empty head slot (the
     * remainder empties the slot); a fixed item is always applied.
     */
    private static void directSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, ModBlocks.DIRECT_SPAWN_MARKER.get());
        SpawnMarkerBlockEntity marker = helper.getBlockEntity(pos, SpawnMarkerBlockEntity.class);
        marker.setItem(SpawnMarkerBlockEntity.EGG_SLOT, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 40));
        marker.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.MAINHAND), new ItemStack(Items.IRON_SWORD));
        marker.setEquipmentList(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD),
                EquipmentList.EMPTY.with(0, new ItemStack(Items.GOLDEN_HELMET), 500));
        SpawnMarkerResolvers.resolveDirect(context(level), helper.absolutePos(pos));

        List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, new AABB(helper.absolutePos(pos)).inflate(4));
        long helmets = zombies.stream().filter(zombie -> zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)).count();
        long bare = zombies.stream().filter(zombie -> zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()).count();
        try {
            helper.assertTrue(zombies.size() == 40, "Expected 40 zombies, got " + zombies.size());
            helper.assertTrue(helmets >= 8 && helmets <= 32 && helmets + bare == 40,
                    "Head list not rolled per mob: " + helmets + " helmets, " + bare + " bare");
            helper.assertTrue(zombies.stream().allMatch(zombie -> zombie.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)),
                    "Fixed item not applied to every mob");
        } finally {
            zombies.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * A spawner carries lists and the row's equipment table: every spawned mob gets the table's chestplate and its
     * own roll of the head list.
     */
    private static void spawnerAndTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, ModBlocks.SPAWNER_MARKER.get());
        SpawnMarkerBlockEntity marker = helper.getBlockEntity(pos, SpawnMarkerBlockEntity.class);
        marker.setItem(SpawnMarkerBlockEntity.EGG_SLOT, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 2));
        marker.setEquipmentList(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD),
                EquipmentList.EMPTY.with(0, new ItemStack(Items.GOLDEN_HELMET), 500));
        marker.setEquipmentTable(0, Optional.of(DIAMOND_CHEST));
        SpawnMarkerResolvers.resolveSpawner(context(level), helper.absolutePos(pos));
        SpawnerBlockEntity spawner = helper.getBlockEntity(pos, SpawnerBlockEntity.class);
        CompoundTag entity = spawnData(level, spawner).entityToSpawn();

        int helmets = 0;
        for (int i = 0; i < 30; i++) {
            Mob mob;
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
                ValueInput input = TagValueInput.create(reporter, level.registryAccess(), entity.copy());
                mob = (Mob) EntityType.loadEntityRecursive(input, level, EntitySpawnReason.SPAWNER, e -> e);
            }
            mob.setPos(helper.absoluteVec(new Vec3(4.5, 2, 6.5)));
            EventHooks.finalizeMobSpawnSpawner(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWNER, null,
                    spawner.getSpawner(), false);
            helper.assertTrue(mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "Equipment table not applied");
            if (mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)) {
                helmets++;
            }
        }
        helper.assertTrue(helmets > 3 && helmets < 27, "Head list not rolled per spawned mob: " + helmets + " of 30");
        helper.succeed();
    }

    /**
     * Importing a vanilla spawner whose spawn data has an equipment table keeps the table on the marker row.
     */
    private static void importTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepareFloor(helper);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, Blocks.SPAWNER);
        SpawnerBlockEntity spawner = helper.getBlockEntity(pos, SpawnerBlockEntity.class);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        CompoundTag data = new CompoundTag();
        data.store(BaseSpawner.SPAWN_DATA_TAG, SpawnData.CODEC,
                new SpawnData(entity, Optional.empty(), Optional.of(new EquipmentTable(DIAMOND_CHEST, 0.0F))));
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            spawner.getSpawner().load(level, helper.absolutePos(pos), TagValueInput.create(reporter, level.registryAccess(), data));
        }

        ServerLevel target = level.getServer().getLevel(Level.END);
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(4, 2, 4)));
        WorldImport.Result result = WorldImport.importInto(level, box, target, 2000, 0);
        BoundingBox placed = result.placed();
        try {
            helper.assertTrue(target.getBlockEntity(new BlockPos(placed.minX(), placed.minY(), placed.minZ())) instanceof SpawnMarkerBlockEntity marker
                    && marker.equipmentTable(0).equals(Optional.of(DIAMOND_CHEST)), "Equipment table was not imported");
        } finally {
            for (Entity e : target.getEntities((Entity) null, AABB.of(placed).inflate(1), e -> !(e instanceof Player))) {
                e.discard();
            }
            BlockPos.betweenClosed(placed.minX(), placed.minY(), placed.minZ(), placed.maxX(), placed.maxY(), placed.maxZ())
                    .forEach(p -> target.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
        }
        helper.succeed();
    }

    private static SpawnData spawnData(ServerLevel level, SpawnerBlockEntity spawner) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            spawner.getSpawner().save(output);
            return output.buildResult().read(BaseSpawner.SPAWN_DATA_TAG, SpawnData.CODEC).orElseThrow();
        }
    }

    private static void prepareFloor(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 5; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static MarkerContext context(ServerLevel level) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE,
                InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }
}

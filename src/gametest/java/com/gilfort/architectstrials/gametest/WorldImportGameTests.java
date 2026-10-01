package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.editor.SelectionToolItem;
import com.gilfort.architectstrials.editor.WorldImport;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.marker.TrialSpawnerMarkerResolver;
import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-20 (importing world areas into the editor). The GameTest server has no editor dimension, so
 * the End serves as import target.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class WorldImportGameTests {

    private static final ResourceKey<LootTable> POOL = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/simple_dungeon"));

    private WorldImportGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "world_import_area", WorldImportGameTests::importArea);
            register(helper, "world_import_selection", WorldImportGameTests::selection);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * An area with a pool-loot chest, an item frame, a spawner, a trial spawner and a mob arrives in the editor
     * with its loot table and frame, the spawners converted to markers and without the mob.
     */
    private static void importArea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        helper.setBlock(1, 2, 1, Blocks.CHEST);
        helper.getBlockEntity(new BlockPos(1, 2, 1), ChestBlockEntity.class).setLootTable(POOL, 0L);
        helper.setBlock(3, 2, 1, Blocks.STONE);
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(3, 2, 2)), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.EMERALD));
        level.addFreshEntity(frame);

        MarkerContext context = context(level, helper.absolutePos(BlockPos.ZERO));
        BlockPos spawnerPos = new BlockPos(5, 2, 1);
        helper.setBlock(spawnerPos, ModBlocks.SPAWNER_MARKER.get());
        SpawnMarkerBlockEntity spawnerMarker = helper.getBlockEntity(spawnerPos, SpawnMarkerBlockEntity.class);
        spawnerMarker.setItem(0, new ItemStack(Items.SKELETON_SPAWN_EGG, 3));
        spawnerMarker.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD), new ItemStack(Items.GOLDEN_HELMET));
        SpawnMarkerResolvers.resolveSpawner(context, helper.absolutePos(spawnerPos));

        BlockPos trialPos = new BlockPos(1, 2, 5);
        helper.setBlock(trialPos, ModBlocks.TRIAL_SPAWNER_MARKER.get());
        TrialSpawnerMarkerBlockEntity trialMarker = helper.getBlockEntity(trialPos, TrialSpawnerMarkerBlockEntity.class);
        trialMarker.setItem(0, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 4));
        trialMarker.setItem(MarkerSlot.ROW_SIZE, new ItemStack(Items.SKELETON_SPAWN_EGG, 2));
        trialMarker.setSimultaneousMobs(3);
        trialMarker.setLootTableReference(Optional.of(POOL));
        TrialSpawnerMarkerResolver.resolve(context, helper.absolutePos(trialPos));
        helper.assertBlockPresent(Blocks.SPAWNER, spawnerPos);
        helper.assertBlockPresent(Blocks.TRIAL_SPAWNER, trialPos);

        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setPos(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);

        ServerLevel target = level.getServer().getLevel(Level.END);
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 4, 6)));
        WorldImport.Result result = WorldImport.importInto(level, box, target, 1000, 0);
        BoundingBox placed = result.placed();
        BlockPos origin = new BlockPos(placed.minX(), placed.minY(), placed.minZ());
        try {
            helper.assertTrue(result.copiedBlocks() == 53, "Expected 53 copied blocks, got " + result.copiedBlocks());
            helper.assertTrue(result.spawners() == 2 && result.missingEggs().isEmpty(), "Spawners not converted: " + result);
            helper.assertTrue(target.getBlockEntity(origin.offset(1, 1, 1)) instanceof ChestBlockEntity chest && POOL.equals(chest.getLootTable()),
                    "Chest lost its loot table");

            helper.assertTrue(target.getBlockEntity(origin.offset(5, 1, 1)) instanceof SpawnMarkerBlockEntity marker
                    && marker.egg().is(Items.SKELETON_SPAWN_EGG) && marker.egg().getCount() == 3
                    && marker.equipment().getOrDefault(EquipmentSlot.HEAD, ItemStack.EMPTY).is(Items.GOLDEN_HELMET),
                    "Spawner was not converted to a Spawner Marker with egg count and equipment");

            if (!(target.getBlockEntity(origin.offset(1, 1, 5)) instanceof TrialSpawnerMarkerBlockEntity trial)) {
                helper.fail("Trial spawner was not converted to a Trial Spawner Marker");
                return;
            }
            helper.assertTrue(trial.egg(0).is(Items.ZOMBIE_SPAWN_EGG) && trial.egg(0).getCount() == 4
                    && trial.egg(1).is(Items.SKELETON_SPAWN_EGG) && trial.egg(1).getCount() == 2,
                    "Trial spawner rows wrong: " + trial.egg(0) + ", " + trial.egg(1));
            helper.assertTrue(trial.simultaneousMobs() == 3 && trial.lootTableReference().equals(Optional.of(POOL)),
                    "Trial spawner settings not taken over");

            AABB area = AABB.of(placed);
            helper.assertTrue(target.getEntitiesOfClass(ItemFrame.class, area).size() == 1, "Item frame was not copied");
            helper.assertTrue(target.getEntitiesOfClass(Zombie.class, area).isEmpty(), "Mob was copied");
        } finally {
            for (Entity entity : target.getEntities((Entity) null, AABB.of(placed).inflate(1), entity -> !(entity instanceof Player))) {
                entity.discard();
            }
            BlockPos.betweenClosed(placed.minX(), placed.minY(), placed.minZ(), placed.maxX(), placed.maxY(), placed.maxZ())
                    .forEach(pos -> target.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16));
            frame.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    /**
     * Corners belong to one dimension (a corner elsewhere starts over); boxes wider than 128 blocks are too large.
     */
    private static void selection(GameTestHelper helper) {
        Selection first = Selection.withCorner(null, Level.OVERWORLD, new BlockPos(0, 0, 0), true);
        Selection both = Selection.withCorner(first, Level.OVERWORLD, new BlockPos(127, 10, 5), false);
        helper.assertTrue(both.box().isPresent() && !SelectionToolItem.tooLarge(both.box().get()), "128 blocks wide must be allowed");
        Selection wide = Selection.withCorner(both, Level.OVERWORLD, new BlockPos(128, 10, 5), false);
        helper.assertTrue(SelectionToolItem.tooLarge(wide.box().orElseThrow()), "129 blocks wide must be too large");
        Selection other = Selection.withCorner(both, Level.NETHER, new BlockPos(1, 1, 1), false);
        helper.assertTrue(other.first().isEmpty() && other.box().isEmpty(), "Corner in another dimension did not start a new selection");
        helper.succeed();
    }

    private static MarkerContext context(ServerLevel level, BlockPos origin) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, origin, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE,
                InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }
}

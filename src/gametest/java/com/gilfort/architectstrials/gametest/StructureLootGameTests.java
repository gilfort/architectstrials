package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-17 (pool loot and guaranteed loot in structures).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class StructureLootGameTests {

    private static final ResourceKey<LootTable> POOL = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/simple_dungeon"));

    private StructureLootGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("structure_loot_pool_and_guaranteed")),
                (Consumer<GameTestHelper>) StructureLootGameTests::poolAndGuaranteed));
    }

    /**
     * A captured structure placed twice: the pool-loot chest keeps its loot table but gets a different fresh seed
     * per placement (rolled on first opening), while the hand-filled chest and the item frame are identical.
     */
    private static void poolAndGuaranteed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        BlockPos poolPos = new BlockPos(1, 2, 1);
        BlockPos fixedPos = new BlockPos(3, 2, 1);
        helper.setBlock(poolPos, Blocks.CHEST);
        helper.getBlockEntity(poolPos, ChestBlockEntity.class).setLootTable(POOL, 0L);
        helper.setBlock(fixedPos, Blocks.CHEST);
        helper.getBlockEntity(fixedPos, ChestBlockEntity.class).setItem(4, new ItemStack(Items.DIAMOND, 3));
        helper.setBlock(1, 2, 3, Blocks.STONE);
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(1, 2, 4)), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.EMERALD));
        level.addFreshEntity(frame);

        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(3, 2, 4)));
        StructureTemplate template = EditorCapture.capture(level, area).orElseThrow().template();
        frame.discard();

        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 6));
        BlockPos second = helper.absolutePos(new BlockPos(6, 2, 6));
        for (BlockPos origin : List.of(first, second)) {
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        }

        ChestBlockEntity pool1 = (ChestBlockEntity) level.getBlockEntity(first);
        ChestBlockEntity pool2 = (ChestBlockEntity) level.getBlockEntity(second);
        helper.assertTrue(POOL.equals(pool1.getLootTable()) && POOL.equals(pool2.getLootTable()),
                "Pool chest lost its loot table reference");
        helper.assertTrue(pool1.getLootTableSeed() != 0L && pool1.getLootTableSeed() != pool2.getLootTableSeed(),
                "Pool chests were not reseeded per placement: " + pool1.getLootTableSeed() + " / " + pool2.getLootTableSeed());
        pool1.unpackLootTable(null);
        pool2.unpackLootTable(null);
        helper.assertFalse(pool1.isEmpty() || pool2.isEmpty(), "Pool loot was not rolled on opening");

        for (BlockPos origin : List.of(first, second)) {
            ChestBlockEntity fixed = (ChestBlockEntity) level.getBlockEntity(origin.offset(2, 0, 0));
            helper.assertTrue(fixed.getLootTable() == null && ItemStack.matches(fixed.getItem(4), new ItemStack(Items.DIAMOND, 3)),
                    "Hand-filled chest is not reproduced identically at " + origin);
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(origin.offset(0, 0, 3)).inflate(0.5));
            helper.assertTrue(frames.size() == 1 && frames.getFirst().getItem().is(Items.EMERALD),
                    "Item frame with its item is not reproduced at " + origin);
            frames.forEach(ItemFrame::discard);
        }
        helper.succeed();
    }
}

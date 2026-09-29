package com.gilfort.architectstrials.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotGrid;
import com.gilfort.architectstrials.slot.SlotManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-04 (slot management in the offset grid).
 * <p>
 * The slot logic is dimension-agnostic; the lifecycle test runs in the overworld because its flat terrain
 * is cheap to clear (the GameTest nether has full terrain).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SlotGameTests {

    private SlotGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "slot_grid_spiral", SlotGameTests::gridSpiral);
            register(helper, "slot_lifecycle", SlotGameTests::lifecycle);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The spiral starts at the origin, never repeats a cell and never moves back to an inner ring.
     */
    private static void gridSpiral(GameTestHelper helper) {
        Set<Long> seen = new HashSet<>();
        int previousRing = 0;
        int[] origin = SlotGrid.cell(0);
        helper.assertTrue(origin[0] == 0 && origin[1] == 0, "Index 0 is not the origin");
        for (int index = 0; index < 289; index++) {
            int[] cell = SlotGrid.cell(index);
            int ring = Math.max(Math.abs(cell[0]), Math.abs(cell[1]));
            helper.assertTrue(seen.add(((long) cell[0] << 32) | (cell[1] & 0xFFFFFFFFL)), "Cell repeated at index " + index);
            helper.assertTrue(ring >= previousRing, "Spiral moved inwards at index " + index);
            previousRing = ring;
        }
        helper.assertTrue(previousRing == 8, "289 cells must fill exactly rings 0 to 8");
        helper.succeed();
    }

    /**
     * Allocate → cap → release → clear → reallocate yields a clean slot with the lowest free index.
     */
    private static void lifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Slot first = SlotManager.allocate(level).orElseThrow();
        Slot second = SlotManager.allocate(level).orElseThrow();
        helper.assertTrue(second.index() == first.index() + 1, "Slots are not allocated in ascending order");

        int previousLimit = ArchitectsTrialsConfig.MAX_CONCURRENT_INSTANCES.getAsInt();
        ArchitectsTrialsConfig.MAX_CONCURRENT_INSTANCES.set(2);
        boolean capped = SlotManager.allocate(level).isEmpty();
        ArchitectsTrialsConfig.MAX_CONCURRENT_INSTANCES.set(previousLimit);
        helper.assertTrue(capped, "maxConcurrentInstances was not respected");

        BlockPos stone = new BlockPos(first.centerX() + 10, 5, first.centerZ() + 10);
        BlockPos chest = new BlockPos(first.centerX(), 6, first.centerZ());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.DIAMOND));
        }
        ArmorStand armorStand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        armorStand.setPos(first.centerX() + 0.5, 8, first.centerZ() + 0.5);
        level.addFreshEntity(armorStand);

        helper.assertTrue(SlotManager.release(level, first.index()), "Release of an occupied slot failed");
        helper.assertTrue(SlotManager.isClearing(level, first.index()), "Released slot is not clearing");
        helper.assertFalse(SlotManager.release(level, first.index()), "Slot was released twice");
        Slot third = SlotManager.allocate(level).orElseThrow();
        helper.assertTrue(third.index() != first.index(), "A clearing slot was allocated");

        AABB area = AABB.of(first.area(level.getMinY(), level.getMaxY()));
        helper.succeedWhen(() -> {
            helper.assertFalse(SlotManager.isClearing(level, first.index()), "Slot is still clearing");
            helper.assertTrue(level.getBlockState(stone).isAir(), "Block was not removed");
            helper.assertTrue(level.getBlockState(chest).isAir(), "Chest was not removed");
            helper.assertTrue(level.getEntitiesOfClass(ArmorStand.class, area).isEmpty(), "Entity was not removed");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                    "Items were dropped while clearing");
            Slot reused = SlotManager.allocate(level).orElseThrow();
            helper.assertTrue(reused.index() == first.index(), "Cleared slot was not reused as lowest free index");
            SlotManager.release(level, reused.index());
            SlotManager.release(level, second.index());
            SlotManager.release(level, third.index());
        });
    }
}

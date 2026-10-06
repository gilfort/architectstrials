package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.InstancePlacements;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotGrid;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-28 (structure placement spread over ticks, restart handling, slot lookup by position).
 * <p>
 * Uses {@code minecraft:the_nether} tier 4, whose only structure is a 48×17×48 platform of about 37,000 blocks —
 * more than two ticks of placement budget.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class PlacementGameTests {

    private static final int LARGE_TIER = 4;

    private PlacementGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "placement_spreads_over_ticks", PlacementGameTests::spreadsOverTicks);
            register(helper, "placement_interrupted_is_discarded", PlacementGameTests::interruptedIsDiscarded);
            register(helper, "slot_lookup_by_position", PlacementGameTests::slotLookupByPosition);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A new instance is registered right away but not ready: nothing is placed in the creation tick and it cannot
     * be entered. Over the following ticks the structure is placed and the instance becomes ready with its spawn
     * points.
     */
    private static void spreadsOverTicks(GameTestHelper helper) {
        ServerLevel level = TestPlayers.challengeLevel(helper);
        ChallengeInstance created = createSpread(level);
        long startTick = helper.getTick();
        helper.assertFalse(created.ready(), "Instance was ready in its creation tick");
        helper.assertTrue(created.state() == ChallengeInstance.LifecycleState.FORMING, "Instance is enterable before placement");
        helper.assertTrue(InstancePlacements.isPlacing(created.id()), "Placement was not queued");
        helper.assertTrue(level.getBlockState(created.origin()).isAir(), "Blocks were placed in the creation tick");

        helper.succeedWhen(() -> {
            ChallengeInstance instance = InstanceManager.data(level).get(created.id()).orElseThrow();
            helper.assertTrue(instance.ready(), "Instance did not become ready");
            helper.assertFalse(InstancePlacements.isPlacing(instance.id()), "Placement still queued after the instance became ready");
            helper.assertTrue(instance.spawnPoints().size() == 2, "Expected 2 spawn points, got " + instance.spawnPoints());
            helper.assertTrue(level.getBlockState(instance.origin()).is(Blocks.STONE), "Lowest corner was not placed");
            helper.assertTrue(level.getBlockState(instance.origin().offset(47, 15, 47)).is(Blocks.STONE), "Highest corner was not placed");
            helper.assertTrue(helper.getTick() - startTick >= 2, "Placement did not take several ticks");
            InstanceManager.close(level, instance.id());
        });
    }

    /**
     * A placement interrupted by a server stop leaves a not-ready instance; on the next start it is discarded and
     * its slot is cleared.
     */
    private static void interruptedIsDiscarded(GameTestHelper helper) {
        ServerLevel level = TestPlayers.challengeLevel(helper);
        ChallengeInstance created = createSpread(level);
        InstancePlacements.abandon(level.getServer(), created.id());
        helper.assertTrue(InstanceManager.data(level).get(created.id()).isPresent(), "Instance vanished with its placement");

        InstancePlacements.discardUnfinished(level.getServer());
        helper.assertTrue(InstanceManager.data(level).get(created.id()).isEmpty(), "Unfinished instance was not discarded");
        helper.assertTrue(SlotManager.isClearing(level, created.slot()), "Slot of the discarded instance is not being cleared");
        helper.succeed();
    }

    /**
     * The slot index computed from a position is the inverse of the spiral layout, and {@link InstanceManager#findAt}
     * finds an instance inside its slot area but not between slots.
     */
    private static void slotLookupByPosition(GameTestHelper helper) {
        for (int index = 0; index < 2_000; index++) {
            int[] cell = SlotGrid.cell(index);
            helper.assertTrue(SlotGrid.index(cell[0], cell[1]) == index, "Cell of slot " + index + " maps back to " + SlotGrid.index(cell[0], cell[1]));
        }
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeTheme theme = ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow();
        InstanceCreation result = InstanceManager.create(nether, theme, 2, nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        ChallengeInstance instance = ((InstanceCreation.Success) result).instance();
        Slot slot = SlotManager.slot(nether, instance.slot());

        BlockPos center = new BlockPos(slot.centerX(), 70, slot.centerZ());
        BlockPos edge = new BlockPos(slot.centerX() + Slot.HALF_EXTENT - 1, 70, slot.centerZ() - Slot.HALF_EXTENT);
        BlockPos outside = new BlockPos(slot.centerX() + Slot.HALF_EXTENT, 70, slot.centerZ());
        helper.assertTrue(InstanceManager.findAt(nether, center).map(ChallengeInstance::id).filter(instance.id()::equals).isPresent(),
                "Instance not found at its slot center");
        helper.assertTrue(InstanceManager.findAt(nether, edge).map(ChallengeInstance::id).filter(instance.id()::equals).isPresent(),
                "Instance not found at the edge of its slot area");
        helper.assertTrue(InstanceManager.findAt(nether, outside).filter(found -> found.id().equals(instance.id())).isEmpty(),
                "Instance found outside its slot area");
        InstanceManager.close(nether, instance.id());
        helper.succeed();
    }

    private static ChallengeInstance createSpread(ServerLevel level) {
        ChallengeTheme theme = ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow();
        InstancePlacements.setImmediate(false);
        InstanceCreation result;
        try {
            result = InstanceManager.create(level, theme, LARGE_TIER, level.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        } finally {
            InstancePlacements.setImmediate(true);
        }
        if (result instanceof InstanceCreation.Success(ChallengeInstance instance)) {
            return instance;
        }
        throw new IllegalStateException("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
    }
}

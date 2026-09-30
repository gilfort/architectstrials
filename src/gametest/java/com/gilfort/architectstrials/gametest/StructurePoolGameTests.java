package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-05 (structure pool, random selection and slot placement).
 * <p>
 * The test datapack declares two structures for the theme {@code minecraft:the_nether} (tier 1 and tier 3),
 * both referencing the template {@code architectstrials:gametest/pool_test}, which the test builds in memory.
 * A lodestone in that template acts as stand-in marker to verify the marker pass.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class StructurePoolGameTests {

    private static final Identifier TEMPLATE_ID = ArchitectsTrials.id("gametest/pool_test");
    private static final Vec3i TEMPLATE_SIZE = new Vec3i(5, 2, 5);
    private static final BlockPos MARKER_OFFSET = new BlockPos(2, 1, 2);

    private StructurePoolGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> register(helper, "structure_pool_placement", StructurePoolGameTests::placement));
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Tiers derive from the pool; creating an instance allocates a slot, places the structure centered at the
     * configured Y, runs the marker pass and persists the instance; an empty pool fails without side effects.
     */
    private static void placement(GameTestHelper helper) {
        buildTemplate(helper);
        AtomicInteger resolvedMarkers = new AtomicInteger();
        MarkerResolvers.register(Blocks.LODESTONE, (context, pos) -> {
            context.level().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            resolvedMarkers.incrementAndGet();
        });

        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeTheme theme = ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow();
        helper.assertTrue(ChallengeStructures.tiers(theme.id()).equals(new TreeSet<>(List.of(1, 3))),
                "Tiers were not derived from the pool: " + ChallengeStructures.tiers(theme.id()));

        int occupiedBefore = SlotManager.data(nether).occupied().size();
        InstanceCreation empty = InstanceManager.create(nether, theme, 2, nether.getRandom());
        helper.assertTrue(empty instanceof InstanceCreation.Failure, "Empty pool did not fail");
        helper.assertTrue(SlotManager.data(nether).occupied().size() == occupiedBefore, "Failed creation left a slot occupied");

        InstanceCreation result = InstanceManager.create(nether, theme, 1, nether.getRandom());
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance instance))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        Slot slot = SlotManager.slot(nether, instance.slot());
        BlockPos expectedOrigin = new BlockPos(slot.centerX() - TEMPLATE_SIZE.getX() / 2,
                ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt(), slot.centerZ() - TEMPLATE_SIZE.getZ() / 2);
        helper.assertTrue(instance.origin().equals(expectedOrigin), "Structure is not centered at the configured Y: " + instance.origin());
        helper.assertTrue(SlotManager.data(nether).occupied().contains(instance.slot()), "Slot was not allocated");
        helper.assertTrue(nether.getBlockState(expectedOrigin).is(Blocks.STONE), "Structure was not placed");
        helper.assertTrue(nether.getBlockState(expectedOrigin.offset(MARKER_OFFSET)).is(Blocks.GOLD_BLOCK), "Marker was not resolved");
        helper.assertTrue(resolvedMarkers.get() == 1, "Marker pass resolved " + resolvedMarkers.get() + " markers instead of 1");
        helper.assertTrue(InstanceManager.data(nether).get(instance.id()).isPresent(), "Instance was not persisted");

        SlotManager.release(nether, instance.slot());
        helper.succeed();
    }

    /**
     * Builds the test template in memory: a 5×5 stone floor with a lodestone marker in the middle above it.
     */
    private static void buildTemplate(GameTestHelper helper) {
        for (int x = 0; x < TEMPLATE_SIZE.getX(); x++) {
            for (int z = 0; z < TEMPLATE_SIZE.getZ(); z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        helper.setBlock(MARKER_OFFSET.above(), Blocks.LODESTONE);
        StructureTemplate template = helper.getLevel().getServer().getStructureTemplateManager().getOrCreate(TEMPLATE_ID);
        template.fillFromWorld(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)), TEMPLATE_SIZE, false, List.of());
    }
}

package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ExitGroup;
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for combined exit portals (US-08 follow-up): adjacent exit bases form one {@code n × (n + 1)}
 * portal, lines longer than three are split, differing facings do not combine, and a redstone signal at any
 * base locks the whole group.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ExitGroupGameTests {

    private ExitGroupGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("exit_groups")),
                (Consumer<GameTestHelper>) ExitGroupGameTests::exitGroups));
    }

    private static void exitGroups(GameTestHelper helper) {
        // Two bases facing north side by side (along X) form one 2x3 portal.
        place(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        place(helper, new BlockPos(2, 1, 1), Direction.NORTH);
        ExitGroup pair = group(helper, new BlockPos(2, 1, 1));
        helper.assertTrue(pair.anchor().equals(helper.absolutePos(new BlockPos(1, 1, 1))), "Pair anchor is not the lowest base");
        helper.assertTrue(pair.width() == 2 && pair.height() == 3, "Pair is not a 2x3 portal");
        AABB expected = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(2, 4, 1)));
        helper.assertTrue(pair.portalArea().equals(expected), "Pair portal area is wrong: " + pair.portalArea());
        helper.assertFalse(pair.locked(), "Unpowered pair is locked");

        // A signal at one base locks the whole group.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(group(helper, new BlockPos(1, 1, 1)).locked(), "Signal at one base did not lock the group");

        // Four in a row split into a group of three and a group of one.
        for (int x = 1; x <= 4; x++) {
            place(helper, new BlockPos(x, 1, 5), Direction.NORTH);
        }
        helper.assertTrue(group(helper, new BlockPos(2, 1, 5)).width() == 3, "First group of a line of four is not three wide");
        ExitGroup rest = group(helper, new BlockPos(4, 1, 5));
        helper.assertTrue(rest.width() == 1 && rest.anchor().equals(helper.absolutePos(new BlockPos(4, 1, 5))),
                "Fourth base does not form its own group");

        // A neighbour with a different facing does not combine.
        place(helper, new BlockPos(1, 1, 8), Direction.NORTH);
        place(helper, new BlockPos(2, 1, 8), Direction.EAST);
        helper.assertTrue(group(helper, new BlockPos(1, 1, 8)).width() == 1, "Bases with different facings were combined");
        helper.succeed();
    }

    private static void place(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = ModBlocks.CHALLENGE_EXIT.get().defaultBlockState().setValue(ChallengeExitBlock.FACING, facing);
        helper.setBlock(pos, state);
    }

    private static ExitGroup group(GameTestHelper helper, BlockPos relative) {
        return ExitGroup.find(helper.getLevel(), helper.absolutePos(relative)).orElseThrow();
    }
}

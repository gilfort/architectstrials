package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ExitGroup;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.run.RunCompletedEvent;
import com.gilfort.architectstrials.run.RunCompletion;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-08 (exit marker with redstone lock and "Run Completed").
 * <p>
 * The dev spawn platform ({@code minecraft:the_nether} tier 2) has two adjacent exit markers at (4,1,8) and
 * (5,1,8), forming one 2×3 exit portal.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ExitGameTests {

    private static final int PLATFORM_TIER = 2;
    private static final BlockPos EXIT_OFFSET = new BlockPos(4, 1, 8);
    private static final AtomicInteger COMPLETED_EVENTS = new AtomicInteger();

    private ExitGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("exit_redstone_lock_and_completion")),
                (Consumer<GameTestHelper>) ExitGameTests::redstoneLockAndCompletion));
    }

    /**
     * Counts fired run-completed events.
     *
     * @param event the run completed event
     */
    @SubscribeEvent
    static void onRunCompleted(RunCompletedEvent event) {
        COMPLETED_EVENTS.incrementAndGet();
    }

    /**
     * The exit marker becomes an open exit; a powered exit cannot be used; walking through an open exit returns
     * the player, counts the run and fires the event.
     */
    private static void redstoneLockAndCompletion(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(),
                PLATFORM_TIER, nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance instance))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        BlockPos exit = instance.origin().offset(EXIT_OFFSET);
        helper.assertTrue(instance.exits().size() == 2 && instance.exits().containsAll(List.of(exit, exit.east())),
                "Exits were not recorded: " + instance.exits());
        helper.assertTrue(ChallengeExitBlock.isOpen(nether.getBlockState(exit)), "Exit marker did not become an open exit");
        helper.assertTrue(ExitGroup.find(nether, exit).orElseThrow().width() == 2, "Adjacent exits did not combine into one portal");

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        InstanceManager.join(player, nether, instance);
        player.hasChangedDimension();
        Vec3 inExit = Vec3.atBottomCenterOf(exit.above());

        nether.setBlock(exit.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertFalse(ChallengeExitBlock.isOpen(nether.getBlockState(exit)), "Redstone signal did not lock the exit");
        TestPlayers.teleport(player, nether, inExit);
        RunCompletion.checkExits(nether);
        helper.assertTrue(player.level() == nether, "A locked exit let the player out");

        int eventsBefore = COMPLETED_EVENTS.get();
        nether.setBlock(exit.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(ChallengeExitBlock.isOpen(nether.getBlockState(exit)), "Removing the signal did not unlock the exit");
        RunCompletion.checkExits(nether);
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player was not returned through the open exit");
        helper.assertTrue(player.getData(ModAttachments.RUN_STATISTICS).completed(Level.NETHER.identifier(), PLATFORM_TIER) == 1,
                "Completed run was not counted");
        helper.assertTrue(COMPLETED_EVENTS.get() == eventsBefore + 1, "RunCompletedEvent was not fired exactly once");

        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, player);
    }
}

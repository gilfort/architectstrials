package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitCamouflage;
import com.gilfort.architectstrials.block.ExitMarkerBlock;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.menu.ExitMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-34 (camouflage for exit blocks).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ExitCamouflageGameTests {

    private ExitCamouflageGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "exit_camouflage_set_and_remove", ExitCamouflageGameTests::setAndRemove);
            register(helper, "exit_camouflage_carried_over", ExitCamouflageGameTests::carriedOver);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A full block camouflages the marker (block state property and block entity); blocks with block entity,
     * non-full or see-through blocks are rejected; the GUI button removes the camouflage; the camouflage survives
     * saving and loading.
     */
    private static void setAndRemove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.EXIT_MARKER.get().defaultBlockState().setValue(ExitMarkerBlock.FACING, Direction.EAST), 3);
        ExitMarkerBlockEntity marker = (ExitMarkerBlockEntity) level.getBlockEntity(pos);

        for (BlockState invalid : List.of(Blocks.CHEST.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                ModBlocks.EXIT_MARKER.get().defaultBlockState())) {
            helper.assertFalse(ExitMarkerBlock.camouflage(level, pos, invalid), "Accepted invalid camouflage " + invalid);
        }
        helper.assertTrue(marker.camouflage().isEmpty(), "Invalid camouflage was stored");
        helper.assertFalse(level.getBlockState(pos).getValue(ExitCamouflage.CAMOUFLAGED), "Marker looks camouflaged without camouflage");

        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        helper.assertTrue(ExitMarkerBlock.camouflage(level, pos, log), "Oak log was rejected as camouflage");
        helper.assertTrue(Optional.of(log).equals(marker.camouflage()), "Camouflage not stored as placed: " + marker.camouflage());
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.getValue(ExitCamouflage.CAMOUFLAGED) && state.getValue(ExitMarkerBlock.FACING) == Direction.EAST,
                "Marker state not updated: " + state);
        helper.assertTrue(level.getBlockEntity(pos) == marker, "Block entity was replaced while setting the camouflage");

        ExitMarkerBlockEntity copy = new ExitMarkerBlockEntity(pos, state);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            copy.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), marker.saveWithoutMetadata(level.registryAccess())));
        }
        helper.assertTrue(Optional.of(log).equals(copy.camouflage()), "Camouflage not saved and loaded: " + copy.camouflage());
        helper.assertTrue(marker.getUpdateTag(level.registryAccess()).contains("camouflage"), "Camouflage is not synced to clients");

        SimpleContainerData data = new SimpleContainerData(ExitMarkerMenu.DATA_COUNT);
        data.set(ExitMarkerMenu.DATA_CAMOUFLAGE_LOW, ExitMarkerMenu.camouflagePart(marker.camouflage(), ExitMarkerMenu.DATA_CAMOUFLAGE_LOW));
        data.set(ExitMarkerMenu.DATA_CAMOUFLAGE_HIGH, ExitMarkerMenu.camouflagePart(marker.camouflage(), ExitMarkerMenu.DATA_CAMOUFLAGE_HIGH));
        ExitMarkerMenu menu = new ExitMarkerMenu(0, ContainerLevelAccess.create(level, pos), data);
        helper.assertTrue(Optional.of(log).equals(menu.camouflage()), "Menu does not show the camouflage: " + menu.camouflage());

        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        menu.clickMenuButton(player, ExitMarkerMenu.BUTTON_REMOVE_CAMOUFLAGE);
        helper.assertTrue(marker.camouflage().isEmpty(), "Remove button did not remove the camouflage");
        helper.assertFalse(level.getBlockState(pos).getValue(ExitCamouflage.CAMOUFLAGED), "Marker still looks camouflaged");
        helper.assertTrue(marker.getUpdateTag(level.registryAccess()).contains("camouflage_sync"), "Removing is not synced to clients");
        TestPlayers.finish(helper, player);
    }

    /**
     * Placing the structure carries the camouflage over to the Challenge Exit, rotated with the structure.
     */
    private static void carriedOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.EXIT_MARKER.get().defaultBlockState(), 3);
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        helper.assertTrue(ExitMarkerBlock.camouflage(level, pos, log), "Oak log was rejected as camouflage");

        ChallengeInstance rotated = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"), 0,
                BlockPos.ZERO, Rotation.CLOCKWISE_90, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE,
                InstanceRoster.EMPTY);
        ExitMarkerBlock.resolve(new MarkerContext(level, rotated, level.getRandom()), pos);

        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.CHALLENGE_EXIT.get()), "Marker did not become an exit");
        helper.assertTrue(level.getBlockState(pos).getValue(ExitCamouflage.CAMOUFLAGED), "Exit does not look camouflaged");
        helper.assertTrue(level.getBlockEntity(pos) instanceof ChallengeExitBlockEntity exit
                && Optional.of(log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z)).equals(exit.camouflage()),
                "Camouflage not carried over rotated");
        helper.succeed();
    }
}

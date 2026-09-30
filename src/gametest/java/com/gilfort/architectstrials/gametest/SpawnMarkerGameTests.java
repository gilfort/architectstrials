package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.SpawnPoint;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-06 (player spawn marker).
 * <p>
 * Uses the dev structure {@code architectstrials:gametest/spawn_platform}: a 9×9 stone brick floor with
 * three spawn markers at (4,1,4) facing north, (1,1,1) facing south and (7,1,7) facing west. It is pooled as
 * {@code minecraft:the_nether} tier 2.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SpawnMarkerGameTests {

    private static final Identifier PLATFORM = ArchitectsTrials.id("gametest/spawn_platform");
    private static final int PLATFORM_TIER = 2;
    private static final List<SpawnPoint> PLATFORM_MARKERS = List.of(
            new SpawnPoint(new BlockPos(4, 1, 4), Direction.NORTH),
            new SpawnPoint(new BlockPos(1, 1, 1), Direction.SOUTH),
            new SpawnPoint(new BlockPos(7, 1, 7), Direction.WEST));

    private SpawnMarkerGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "spawn_markers_resolved", SpawnMarkerGameTests::markersResolved);
            register(helper, "spawn_marker_rotation", SpawnMarkerGameTests::markerRotation);
            register(helper, "spawn_marker_structure_validation", SpawnMarkerGameTests::structureValidation);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Placing a structure records all spawn markers with their facing, removes the marker blocks and makes
     * the instance ready; a joining player lands on one of the spawn points, looking in its direction.
     */
    private static void markersResolved(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeTheme theme = ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow();
        InstanceCreation result = InstanceManager.create(nether, theme, PLATFORM_TIER, nether.getRandom(), InstanceManager.defaultTimeLimitTicks());
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance instance))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        helper.assertTrue(instance.ready(), "Instance is not ready");
        List<SpawnPoint> expected = PLATFORM_MARKERS.stream()
                .map(point -> new SpawnPoint(instance.origin().offset(point.pos()), point.facing())).toList();
        helper.assertTrue(instance.spawnPoints().size() == expected.size() && instance.spawnPoints().containsAll(expected),
                "Spawn points do not match the markers: " + instance.spawnPoints());
        for (SpawnPoint point : expected) {
            helper.assertTrue(nether.getBlockState(point.pos()).isAir(), "Marker was not replaced by air at " + point.pos());
        }
        helper.assertTrue(nether.getBlockState(instance.origin()).is(Blocks.STONE_BRICKS), "Structure was not placed");

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(player, nether, instance), "Player could not join a ready instance");
        SpawnPoint landed = expected.stream()
                .filter(point -> player.position().distanceTo(Vec3.atBottomCenterOf(point.pos())) < 0.01)
                .findFirst().orElse(null);
        helper.assertTrue(landed != null, "Player did not land on a spawn point: " + player.position());
        helper.assertTrue(Math.abs(player.getYRot() - landed.facing().toYRot()) < 0.01, "Player does not face the marker direction");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Player was not switched to Adventure");
        helper.assertTrue(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was not stored");

        ChallengeInstance notReady = instance.withSpawnPoints(List.of());
        helper.assertFalse(InstanceManager.join(player, nether, notReady), "Player joined an instance without spawn points");

        SlotManager.release(nether, instance.slot());
        TestPlayers.finish(helper, player);
    }

    /**
     * Marker positions and facings follow the structure's rotation.
     */
    private static void markerRotation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getServer().getStructureTemplateManager().get(PLATFORM).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90).setMirror(Mirror.NONE);
        BlockPos origin = helper.absolutePos(new BlockPos(9, 1, 1));
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);

        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), PLATFORM_TIER, PLATFORM,
                0, origin, Rotation.CLOCKWISE_90, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, List.of());
        MarkerContext context = new MarkerContext(level, dummy, level.getRandom());
        MarkerResolvers.resolveAll(context, template, origin, settings);

        BlockPos center = StructureTemplate.calculateRelativePosition(settings, new BlockPos(4, 1, 4)).offset(origin);
        helper.assertTrue(context.spawnPoints().contains(new SpawnPoint(center, Direction.EAST)),
                "Center marker facing north was not rotated to east: " + context.spawnPoints());
        helper.succeed();
    }

    /**
     * Structures whose template has no player spawn marker are removed from the pool on validation.
     */
    private static void structureValidation(GameTestHelper helper) {
        Identifier markerless = ArchitectsTrials.id("gametest_validation/tier_1/igloo");
        ChallengeStructures.validate(helper.getLevel().getServer());
        helper.assertTrue(ChallengeStructures.get(markerless).isEmpty(), "Structure without spawn marker was not skipped");
        helper.assertTrue(ChallengeStructures.get(ArchitectsTrials.id("the_nether/tier_2/spawn_platform")).isPresent(),
                "Structure with spawn markers was skipped");
        helper.assertTrue(ModBlocks.PLAYER_SPAWN_MARKER.get() != null, "Marker block is not registered");
        helper.succeed();
    }
}

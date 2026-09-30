package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.portal.PortalEcho;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.gilfort.architectstrials.travel.EntryPoint;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-02 (entry point, return teleport, Adventure mode, exit).
 * <p>
 * {@code minecraft:the_nether} acts as challenge dimension (declared as theme by the test datapack).
 * Tests start in the overworld test area.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeTravelGameTests {

    private ChallengeTravelGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "enter_and_return", ChallengeTravelGameTests::enterAndReturn);
            register(helper, "entry_point_kept_on_reenter", ChallengeTravelGameTests::entryPointKeptOnReenter);
            register(helper, "obstructed_return", ChallengeTravelGameTests::obstructedReturn);
            register(helper, "missing_entry_dimension", ChallengeTravelGameTests::missingEntryDimension);
            register(helper, "exit_without_entry_point", ChallengeTravelGameTests::exitWithoutEntryPoint);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Entering stores the entry point and switches to Adventure; returning restores position and game mode,
     * applies the portal cooldown and clears the entry point.
     */
    private static void enterAndReturn(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        Vec3 start = player.position();

        ChallengeTravel.enter(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION, 0.0F, 0.0F, true);
        helper.assertTrue(player.level().dimension() == Level.NETHER, "Player did not enter the challenge dimension");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Player was not switched to Adventure");
        helper.assertTrue(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was not stored");

        helper.assertTrue(ChallengeTravel.returnToEntryPoint(player), "Return reported no entry point");
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player was not returned to the entry dimension");
        helper.assertTrue(player.position().distanceTo(start) < 0.01,
                "Player was not returned to the exact entry position: expected " + start + ", got " + player.position());
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "Game mode was not restored");
        helper.assertTrue(Mth.degreesDifferenceAbs(player.getYRot(), 180.0F) < 0.01F,
                "Player was not turned around on return: yaw " + player.getYRot());
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(PortalEcho.class, player.getBoundingBox().inflate(2.0)).isEmpty(),
                "No portal echo appeared behind the returning player");
        helper.assertTrue(player.isOnPortalCooldown(), "Return portal cooldown was not applied");
        helper.assertFalse(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was not cleared");
        TestPlayers.finish(helper, player);
    }

    /**
     * Moving within challenge dimensions must not overwrite the original entry point.
     */
    private static void entryPointKeptOnReenter(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        Vec3 start = player.position();

        ChallengeTravel.enter(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION, 0.0F, 0.0F, true);
        ChallengeTravel.enter(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION.add(10, 0, 10), 0.0F, 0.0F, true);
        EntryPoint entry = player.getExistingData(ModAttachments.ENTRY_POINT).orElseThrow();
        helper.assertTrue(entry.dimension() == Level.OVERWORLD, "Entry dimension was overwritten");
        helper.assertTrue(entry.position().distanceTo(start) < 0.01, "Entry position was overwritten");
        helper.assertTrue(entry.gameMode() == GameType.SURVIVAL, "Entry game mode was overwritten");
        TestPlayers.finish(helper, player);
    }

    /**
     * An obstructed entry position is replaced by the nearest position where the player fits.
     */
    private static void obstructedReturn(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        Vec3 start = player.position();

        ChallengeTravel.enter(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION, 0.0F, 0.0F, true);
        helper.setBlock(TestPlayers.START, Blocks.STONE);
        helper.setBlock(TestPlayers.START.above(), Blocks.STONE);

        ChallengeTravel.returnToEntryPoint(player);
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player was not returned to the entry dimension");
        helper.assertTrue(player.position().distanceTo(start) > 0.5, "Player was placed inside the obstruction");
        helper.assertTrue(helper.getLevel().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position())),
                "Player was not placed at a safe position");
        TestPlayers.finish(helper, player);
    }

    /**
     * If the stored entry dimension no longer exists, the player is sent to the world spawn with the stored
     * game mode.
     */
    private static void missingEntryDimension(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        TestPlayers.teleport(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION);
        player.setData(ModAttachments.ENTRY_POINT, new EntryPoint(
                ResourceKey.create(Registries.DIMENSION, ArchitectsTrials.id("gametest_missing")),
                Vec3.ZERO, 0.0F, 0.0F, GameType.SURVIVAL));

        helper.assertTrue(ChallengeTravel.returnToEntryPoint(player), "Return reported no entry point");
        helper.assertTrue(player.level() == player.level().getServer().findRespawnDimension(), "Player was not sent to the world spawn dimension");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Stored game mode was not restored");
        helper.assertFalse(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was not cleared");
        TestPlayers.finish(helper, player);
    }

    /**
     * Exit without a stored entry point sends the player to their respawn point (here: world spawn).
     */
    private static void exitWithoutEntryPoint(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        TestPlayers.teleport(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION);

        helper.assertFalse(ChallengeTravel.exit(player), "Exit reported an entry point although none was stored");
        helper.assertTrue(player.level().dimension() != Level.NETHER, "Player is still inside the challenge dimension");
        TestPlayers.finish(helper, player);
    }

}

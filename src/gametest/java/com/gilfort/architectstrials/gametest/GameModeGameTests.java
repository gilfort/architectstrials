package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-31 (per-challenge game mode). {@code minecraft:the_nether} tier 5 is the spawn platform with
 * {@code "game_mode": "survival"}, tier 2 the same platform in the default Adventure mode.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class GameModeGameTests {

    private static final int SURVIVAL_TIER = 5;
    private static final int ADVENTURE_TIER = 2;
    private static final BlockPos EXIT_OFFSET = new BlockPos(4, 1, 8);

    private GameModeGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "game_mode_entry_and_restore", GameModeGameTests::entryAndRestore);
            register(helper, "game_mode_survival_protection", GameModeGameTests::survivalProtection);
            register(helper, "game_mode_metadata_default", GameModeGameTests::metadataDefault);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A Survival challenge puts players into Survival, an Adventure challenge into Adventure; leaving restores the
     * game mode of the entry point.
     */
    private static void entryAndRestore(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);

        ChallengeInstance survival = create(nether, SURVIVAL_TIER);
        helper.assertTrue(InstanceManager.join(player, nether, survival), "Player could not join the survival challenge");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Survival challenge did not switch to Survival: " + player.gameMode.getGameModeForPlayer());
        helper.assertTrue(ChallengeTravel.returnToEntryPoint(player), "Player could not return");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "Game mode of the entry point was not restored: " + player.gameMode.getGameModeForPlayer());

        ChallengeInstance adventure = create(nether, ADVENTURE_TIER);
        helper.assertTrue(InstanceManager.join(player, nether, adventure), "Player could not join the adventure challenge");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Adventure challenge did not switch to Adventure: " + player.gameMode.getGameModeForPlayer());
        helper.assertTrue(ChallengeTravel.returnToEntryPoint(player), "Player could not return");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "Game mode was not restored after the adventure challenge");

        InstanceManager.close(nether, survival.id());
        InstanceManager.close(nether, adventure.id());
        TestPlayers.finish(helper, player);
    }

    /**
     * In a Survival challenge the floor can be mined, but not the block below a spawn point, the exit base or its
     * portal blocks (which cannot be replaced either).
     */
    private static void survivalProtection(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengeInstance instance = create(nether, SURVIVAL_TIER);
        helper.assertTrue(InstanceManager.join(player, nether, instance), "Player could not join");

        BlockPos belowSpawn = instance.spawnPoints().getFirst().pos().below();
        BlockPos exit = instance.origin().offset(EXIT_OFFSET);
        BlockPos floor = instance.origin().offset(2, 0, 6);
        helper.assertFalse(nether.getBlockState(floor).isAir(), "Test floor block is missing");

        helper.assertFalse(player.gameMode.destroyBlock(belowSpawn), "Block below a spawn point was broken");
        helper.assertFalse(nether.getBlockState(belowSpawn).isAir(), "Block below a spawn point is gone");
        helper.assertFalse(player.gameMode.destroyBlock(exit), "Exit base was broken");
        helper.assertFalse(player.gameMode.destroyBlock(exit.above()), "Exit portal block was broken");
        helper.assertFalse(nether.getBlockState(exit.above()).canBeReplaced(), "Exit portal block can be replaced by placing a block");
        helper.assertTrue(player.gameMode.destroyBlock(floor), "Floor block could not be mined in a Survival challenge");
        helper.assertTrue(nether.getBlockState(floor).isAir(), "Mined floor block is still there");

        ChallengeTravel.returnToEntryPoint(player);
        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, player);
    }

    /**
     * Metadata without {@code game_mode} means Adventure; Creative and Spectator are rejected.
     */
    private static void metadataDefault(GameTestHelper helper) {
        String base = "\"theme\": \"minecraft:the_nether\", \"tier\": 1, \"structure\": \"architectstrials:gametest/spawn_platform\"";
        ChallengeStructure plain = ChallengeStructure.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + base + "}")).getOrThrow();
        helper.assertTrue(plain.gameMode() == GameType.ADVENTURE, "Missing game_mode is not Adventure: " + plain.gameMode());
        ChallengeStructure survival = ChallengeStructure.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{" + base + ", \"game_mode\": \"survival\"}")).getOrThrow();
        helper.assertTrue(survival.gameMode() == GameType.SURVIVAL, "game_mode survival was not read");
        helper.assertTrue(ChallengeStructure.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{" + base + ", \"game_mode\": \"creative\"}")).isError(), "Creative was accepted as challenge game mode");
        helper.succeed();
    }

    private static ChallengeInstance create(ServerLevel nether, int tier) {
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), tier,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (result instanceof InstanceCreation.Success(ChallengeInstance instance)) {
            return instance;
        }
        throw new IllegalStateException("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
    }
}

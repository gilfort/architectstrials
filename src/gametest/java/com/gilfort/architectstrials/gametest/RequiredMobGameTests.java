package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-30 (required mobs seal exits). {@code minecraft:the_nether} tier 6 is a platform with a
 * Direct Spawn Marker (2 zombies, "Required") and an Exit Marker set to require them.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class RequiredMobGameTests {

    private static final int TIER = 6;
    private static final BlockPos EXIT_OFFSET = new BlockPos(4, 1, 8);

    private RequiredMobGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "required_mobs_seal_exit", RequiredMobGameTests::sealExit);
            register(helper, "required_mobs_marker_options", RequiredMobGameTests::markerOptions);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Required mobs glow for the remaining instance time and are counted in the instance; the exit requiring them
     * is sealed and lets nobody out; removing one keeps it sealed, removing the last one unseals it and the run
     * can be completed.
     */
    private static void sealExit(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), TIER,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance created))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        helper.assertTrue(created.requiredMobs().total() == 2 && created.requiredMobs().remaining().size() == 2,
                "Expected 2 required mobs, got " + created.requiredMobs());
        List<Mob> mobs = nether.getEntitiesOfClass(Mob.class, new AABB(created.origin()).inflate(12),
                mob -> created.requiredMobs().remaining().contains(mob.getUUID()));
        helper.assertTrue(mobs.size() == 2, "Required mobs not found in the world: " + mobs.size());
        long remaining = created.deadline() - ChallengeClock.now(nether.getServer());
        for (Mob mob : mobs) {
            MobEffectInstance glowing = mob.getEffect(MobEffects.GLOWING);
            helper.assertTrue(glowing != null && Math.abs(glowing.getDuration() - remaining) <= 20,
                    "Required mob does not glow for the remaining instance time: " + glowing + " vs " + remaining);
        }

        BlockPos exit = created.origin().offset(EXIT_OFFSET);
        helper.assertTrue(nether.getBlockState(exit).getValue(ChallengeExitBlock.SEALED), "Exit requiring the mobs is not sealed");
        helper.assertFalse(ChallengeExitBlock.isOpen(nether.getBlockState(exit)), "Sealed exit counts as open");

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(player, nether, created), "Player could not join");
        helper.assertFalse(ChallengeExitPortalBlock.onPlayerInside(nether, exit.above(), player), "Sealed exit let the player out");

        mobs.get(0).discard();
        ChallengeInstance afterOne = InstanceManager.data(nether).get(created.id()).orElseThrow();
        helper.assertTrue(afterOne.requiredMobs().defeated() == 1, "First removal was not counted: " + afterOne.requiredMobs());
        helper.assertTrue(nether.getBlockState(exit).getValue(ChallengeExitBlock.SEALED), "Exit unsealed before the last required mob");

        mobs.get(1).kill(nether);
        helper.succeedWhen(() -> {
            ChallengeInstance afterAll = InstanceManager.data(nether).get(created.id()).orElseThrow();
            helper.assertTrue(afterAll.requiredMobs().allDefeated(), "Last required mob was not counted: " + afterAll.requiredMobs());
            helper.assertFalse(nether.getBlockState(exit).getValue(ChallengeExitBlock.SEALED), "Exit still sealed after all required mobs");
            helper.assertTrue(ChallengeExitPortalBlock.onPlayerInside(nether, exit.above(), player), "Unsealed exit did not complete the run");
            InstanceManager.close(nether, created.id());
            TestPlayers.finish(helper, player);
        });
    }

    /**
     * Only Direct Spawn Markers support required mobs; the setting is ignored for Spawner Markers.
     */
    private static void markerOptions(GameTestHelper helper) {
        BlockPos direct = new BlockPos(1, 1, 1);
        BlockPos spawner = new BlockPos(3, 1, 1);
        helper.setBlock(direct, ModBlocks.DIRECT_SPAWN_MARKER.get());
        helper.setBlock(spawner, ModBlocks.SPAWNER_MARKER.get());
        SpawnMarkerBlockEntity directMarker = helper.getBlockEntity(direct, SpawnMarkerBlockEntity.class);
        SpawnMarkerBlockEntity spawnerMarker = helper.getBlockEntity(spawner, SpawnMarkerBlockEntity.class);
        helper.assertTrue(directMarker.supportsRequired(), "Direct Spawn Marker does not support required mobs");
        helper.assertFalse(spawnerMarker.supportsRequired(), "Spawner Marker supports required mobs");
        directMarker.setRequired(true);
        spawnerMarker.setRequired(true);
        helper.assertTrue(directMarker.required(), "Direct Spawn Marker could not be set to required");
        helper.assertFalse(spawnerMarker.required(), "Spawner Marker was set to required");
        helper.succeed();
    }
}

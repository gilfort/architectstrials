package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.ForbiddenUse;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.ScrollEffectApplication;
import com.gilfort.architectstrials.marker.RequiredMobTracker;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-44 (challenges stay sealed): foreign travel into and out of challenges, forbidden items and
 * blocks, mobs defused outside. Spawner equipment outside challenges is covered by {@code mob_marker_spawner}, exit
 * participation by {@code exit_redstone_lock_and_completion}.
 * <p>
 * {@code minecraft:the_nether} acts as challenge dimension; the test datapack forbids ender pearls and cobblestone
 * (items) and TNT (blocks).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeSealGameTests {

    /** Platform tier of the test datapack. */
    private static final int PLATFORM_TIER = 2;

    /** Tier whose challenge gives mobs Resistance II. */
    private static final int MOB_EFFECT_TIER = 14;

    /** Free spot in the challenge dimension, away from other tests and instance slots. */
    private static final BlockPos USE_SPOT = new BlockPos(-4000, 100, 0);

    private ChallengeSealGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "seal_foreign_travel", ChallengeSealGameTests::foreignTravel);
            register(helper, "seal_forbidden_use", ChallengeSealGameTests::forbiddenUse);
            register(helper, "seal_mob_defusing", ChallengeSealGameTests::mobDefusing);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A foreign teleport into the challenge dimension is cancelled; the mod's own travel passes. A foreign teleport
     * out of a joined instance restores the game mode, removes the player from the instance and clears the entry
     * point, leaving them where the teleport took them.
     */
    private static void foreignTravel(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        Vec3 start = player.position();

        helper.assertFalse(TestPlayers.foreignTeleport(player, nether, TestPlayers.CHALLENGE_POSITION), "Foreign travel into a challenge was not cancelled");
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player reached the challenge by a foreign teleport");

        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), PLATFORM_TIER,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance created))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        helper.assertTrue(InstanceManager.join(player, nether, created), "Mod travel into the challenge was blocked");
        player.hasChangedDimension();
        helper.assertTrue(player.level() == nether, "Player did not enter through the mod");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Player was not switched to Adventure");

        Vec3 elsewhere = start.add(2.0, 0.0, 0.0);
        helper.assertTrue(TestPlayers.foreignTeleport(player, helper.getLevel(), elsewhere), "Foreign travel out of a challenge was blocked");
        helper.assertTrue(player.level() == helper.getLevel() && player.position().distanceTo(elsewhere) < 0.01,
                "Player did not stay where the foreign teleport took them: " + player.position());
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "Game mode was not restored after a foreign leave");
        helper.assertFalse(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was kept after a foreign leave");
        helper.assertFalse(InstanceManager.data(nether).get(created.id()).orElseThrow().participants().contains(player.getUUID()),
                "Player is still a participant after a foreign leave");

        InstanceManager.close(nether, created.id());
        TestPlayers.finish(helper, player);
    }

    /**
     * Inside a challenge, forbidden items cannot be used in the air or on blocks and forbidden blocks cannot be
     * placed; clicked blocks still react and other items are not affected. Outside, nothing is forbidden.
     */
    private static void forbiddenUse(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        BlockPos floor = USE_SPOT.below();
        BlockPos lever = USE_SPOT.east();
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 0, 1))) {
            nether.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            nether.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            nether.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
        }
        nether.setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengeTravel.enter(player, nether, Vec3.atBottomCenterOf(USE_SPOT), 0.0F, 0.0F, GameType.SURVIVAL);
        player.hasChangedDimension();

        ItemStack pearl = new ItemStack(Items.ENDER_PEARL, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, pearl);
        helper.assertTrue(ForbiddenUse.isForbidden(player, pearl), "Ender pearl is not forbidden inside the challenge");
        helper.assertFalse(ForbiddenUse.isForbidden(player, new ItemStack(Items.SNOWBALL)), "An item outside the tag is forbidden");
        InteractionResult thrown = player.gameMode.useItem(player, nether, pearl, InteractionHand.MAIN_HAND);
        helper.assertTrue(thrown == InteractionResult.FAIL && pearl.getCount() == 4, "Forbidden item was used in the air: " + thrown);

        player.gameMode.useItemOn(player, nether, pearl, InteractionHand.MAIN_HAND, hit(lever, Direction.UP));
        helper.assertTrue(nether.getBlockState(lever).getValue(LeverBlock.POWERED), "Lever did not react to a click with a forbidden item");

        BlockPos target = floor.west();
        ItemStack cobblestone = new ItemStack(Items.COBBLESTONE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, cobblestone);
        player.gameMode.useItemOn(player, nether, cobblestone, InteractionHand.MAIN_HAND, hit(target, Direction.UP));
        helper.assertTrue(nether.getBlockState(target.above()).isAir(), "Forbidden block item was placed");

        ItemStack tnt = new ItemStack(Items.TNT, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, tnt);
        player.gameMode.useItemOn(player, nether, tnt, InteractionHand.MAIN_HAND, hit(target, Direction.UP));
        helper.assertTrue(nether.getBlockState(target.above()).isAir(), "Forbidden block was placed");

        ChallengeTravel.returnToEntryPoint(player);
        helper.assertFalse(ForbiddenUse.isForbidden(player, pearl), "Ender pearl is forbidden outside challenges");
        TestPlayers.finish(helper, player);
    }

    /**
     * A mob that got the challenge's mob effects (and the required-mob glow) loses them when it changes into a
     * level outside challenge dimensions, as after being released from a capture tool.
     */
    private static void mobDefusing(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), MOB_EFFECT_TIER,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance created))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(player, nether, created), "Player could not join");
        player.hasChangedDimension();

        Zombie zombie = EntityTypes.ZOMBIE.create(nether, EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie != null, "Zombie could not be created");
        zombie.setPos(Vec3.atBottomCenterOf(created.origin().above(2)));
        zombie.setPersistenceRequired();
        nether.addFreshEntity(zombie);
        helper.assertTrue(zombie.hasEffect(MobEffects.RESISTANCE), "Challenge mob effect was not applied inside");
        helper.assertTrue(zombie.getPersistentData().contains(ScrollEffectApplication.APPLIED_EFFECTS_KEY), "Applied effects were not recorded");
        // Stands in for a required mob: glow and instance id as given by its marker.
        zombie.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        zombie.getPersistentData().putString(RequiredMobTracker.INSTANCE_KEY, created.id().toString());
        zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));

        Entity outside = zombie.teleport(new TeleportTransition(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)), Vec3.ZERO, 0.0F, 0.0F,
                TeleportTransition.DO_NOTHING));
        try {
            helper.assertTrue(outside instanceof Zombie, "Zombie did not change into the overworld");
            Zombie released = (Zombie) outside;
            helper.assertFalse(released.hasEffect(MobEffects.RESISTANCE), "Challenge mob effect kept outside challenges");
            helper.assertFalse(released.hasEffect(MobEffects.GLOWING), "Required-mob glow kept outside challenges");
            helper.assertFalse(released.getPersistentData().contains(RequiredMobTracker.INSTANCE_KEY), "Required instance id kept outside challenges");
            helper.assertTrue(released.hasEffect(MobEffects.SPEED), "An effect not given by the challenge was removed");
        } finally {
            if (outside != null) {
                outside.discard();
            }
            InstanceManager.close(nether, created.id());
        }
        TestPlayers.finish(helper, player);
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
    }
}

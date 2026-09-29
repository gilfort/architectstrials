package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.gilfort.architectstrials.ward.DimensionWard;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-03 (Dimension Ward, lossless death protection).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class DimensionWardGameTests {

    private static final int INVENTORY_SLOT = 10;
    private static final int INVENTORY_ITEM_COUNT = 5;

    private DimensionWardGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "ward_void_death", DimensionWardGameTests::voidDeath);
            register(helper, "ward_kill_command", DimensionWardGameTests::killCommand);
            register(helper, "ward_real_totem_first", DimensionWardGameTests::realTotemFirst);
            register(helper, "ward_inactive_without_entry_point", DimensionWardGameTests::inactiveWithoutEntryPoint);
            register(helper, "ward_grace_period", DimensionWardGameTests::gracePeriod);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Falling into the void returns the player losslessly with a clean slate.
     */
    private static void voidDeath(GameTestHelper helper) {
        ServerPlayer player = protectedPlayer(helper);
        ServerLevel challenge = TestPlayers.challengeLevel(helper);

        player.hurtServer(challenge, player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        assertSavedAndReturned(helper, player);
        TestPlayers.finish(helper, player);
    }

    /**
     * {@code /kill} (generic kill damage) is covered as well.
     */
    private static void killCommand(GameTestHelper helper) {
        ServerPlayer player = protectedPlayer(helper);

        player.kill(TestPlayers.challengeLevel(helper));
        assertSavedAndReturned(helper, player);
        TestPlayers.finish(helper, player);
    }

    /**
     * A real totem takes precedence: it is consumed and the player stays inside the challenge.
     */
    private static void realTotemFirst(GameTestHelper helper) {
        ServerPlayer player = protectedPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        player.hurtServer(TestPlayers.challengeLevel(helper), player.damageSources().generic(), Float.MAX_VALUE);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Real totem was not consumed");
        helper.assertTrue(player.level().dimension() == Level.NETHER, "Ward triggered although the real totem saved the player");
        helper.assertTrue(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was cleared although the player stayed inside");
        TestPlayers.finish(helper, player);
    }

    /**
     * Without a stored entry point the ward is inactive and the player dies normally.
     */
    private static void inactiveWithoutEntryPoint(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        TestPlayers.teleport(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION);

        helper.assertFalse(DimensionWard.isProtected(player), "Player without entry point must not be protected");
        player.kill(TestPlayers.challengeLevel(helper));
        helper.assertTrue(player.isDeadOrDying(), "Player without entry point was saved");
        TestPlayers.finish(helper, player);
    }

    /**
     * After the ward has triggered, all damage is ignored during the grace period.
     */
    private static void gracePeriod(GameTestHelper helper) {
        ServerPlayer player = protectedPlayer(helper);
        player.kill(TestPlayers.challengeLevel(helper));
        player.hasChangedDimension();

        DamageSource damage = player.damageSources().generic();
        helper.assertFalse(player.hurtServer(helper.getLevel(), damage, 5.0F), "Damage was applied during the grace period");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Health dropped during the grace period");
        TestPlayers.finish(helper, player);
    }

    /**
     * Creates a survival player that entered the challenge dimension, carrying an item, effects, fire and
     * reduced hunger.
     */
    private static ServerPlayer protectedPlayer(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengeTravel.enter(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION, 0.0F, 0.0F, true);
        player.hasChangedDimension();
        player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.DIAMOND, INVENTORY_ITEM_COUNT));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600));
        player.igniteForSeconds(10.0F);
        player.getFoodData().setFoodLevel(3);
        helper.assertTrue(DimensionWard.isProtected(player), "Player inside the challenge is not protected");
        return player;
    }

    private static void assertSavedAndReturned(GameTestHelper helper, ServerPlayer player) {
        helper.assertFalse(player.isDeadOrDying(), "Player died");
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player was not returned to the entry dimension");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Health is not full");
        helper.assertTrue(player.getFoodData().getFoodLevel() == FoodConstants.MAX_FOOD, "Hunger is not full");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "Effects were not removed");
        helper.assertFalse(player.isOnFire(), "Fire was not extinguished");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(INVENTORY_SLOT), new ItemStack(Items.DIAMOND, INVENTORY_ITEM_COUNT)), "Inventory was changed");
        helper.assertFalse(player.hasData(ModAttachments.ENTRY_POINT), "Entry point was not cleared");
        helper.assertTrue(DimensionWard.isInGracePeriod(player), "Grace period was not started");
    }
}

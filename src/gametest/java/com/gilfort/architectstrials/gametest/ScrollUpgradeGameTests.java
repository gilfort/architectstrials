package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ScrollEffect;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.scroll.ScrollTarget;
import com.gilfort.architectstrials.scroll.ScrollUpgradeRecipe;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.EntryPoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-18 (scroll upgrades at the smithing table).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollUpgradeGameTests {

    private static final ResourceKey<Recipe<?>> LUCK_RECIPE = ResourceKey.create(Registries.RECIPE, ArchitectsTrials.id("gametest_scroll_luck"));

    private ScrollUpgradeGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "scroll_upgrade_recipe", ScrollUpgradeGameTests::recipe);
            register(helper, "scroll_effects_applied", ScrollUpgradeGameTests::effectsApplied);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The datapack recipe loads, keeps theme/tier/time limit, sets the option and adds the effect; repeating it
     * or applying a weaker duplicate yields no result, a stronger one replaces the effect.
     */
    private static void recipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ScrollUpgradeRecipe luck = (ScrollUpgradeRecipe) level.getServer().getRecipeManager().byKey(LUCK_RECIPE)
                .orElseThrow(() -> new IllegalStateException("Test recipe not loaded")).value();
        ItemStack scroll = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        scroll.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(Level.NETHER.identifier(), 2));
        scroll.set(ModDataComponents.TIME_LIMIT.get(), 7);

        SmithingRecipeInput input = input(scroll);
        helper.assertTrue(luck.matches(input, level), "Upgrade recipe does not match a scroll");
        ItemStack upgraded = luck.assemble(input);
        helper.assertTrue(new ScrollTarget(Level.NETHER.identifier(), 2).equals(upgraded.get(ModDataComponents.SCROLL_TARGET.get()))
                && Integer.valueOf(7).equals(upgraded.get(ModDataComponents.TIME_LIMIT.get())), "Theme, tier or time limit were lost");
        helper.assertTrue(new ScrollOptions(4, 0, false).equals(upgraded.get(ModDataComponents.SCROLL_OPTIONS.get())), "Option not set");
        ScrollEffects effects = upgraded.getOrDefault(ModDataComponents.SCROLL_EFFECTS.get(), ScrollEffects.NONE);
        helper.assertTrue(effects.entries().size() == 1 && effects.entries().getFirst().effect().equals(MobEffects.LUCK),
                "Luck effect not added: " + effects);

        helper.assertFalse(luck.matches(input(upgraded), level), "Repeating the same upgrade must yield no result");

        ScrollUpgradeRecipe strongLuck = recipe(new ScrollEffect(ScrollEffect.Target.PLAYER, MobEffects.LUCK, -1, 1));
        ItemStack stronger = strongLuck.assemble(input(upgraded));
        helper.assertTrue(stronger.getOrDefault(ModDataComponents.SCROLL_EFFECTS.get(), ScrollEffects.NONE).entries()
                .equals(List.of(new ScrollEffect(ScrollEffect.Target.PLAYER, MobEffects.LUCK, -1, 1))), "Stronger effect did not replace");

        ScrollUpgradeRecipe weakLuck = recipe(new ScrollEffect(ScrollEffect.Target.PLAYER, MobEffects.LUCK, 600, 0));
        helper.assertFalse(weakLuck.matches(input(stronger), level), "Weaker duplicate effect must yield no result");
        helper.assertTrue(weakLuck.assemble(input(stronger)).isEmpty(), "Weaker duplicate effect produced a result");
        helper.succeed();
    }

    /**
     * Player effects are applied on entry and infinite ones removed on leave; mob effects reach existing mobs on
     * the first entry and mobs spawned afterwards.
     */
    private static void effectsApplied(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ScrollEffects effects = new ScrollEffects(List.of(
                new ScrollEffect(ScrollEffect.Target.PLAYER, MobEffects.REGENERATION, -1, 0),
                new ScrollEffect(ScrollEffect.Target.MOBS, MobEffects.SPEED, 1200, 1)));
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), 2,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT, effects);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance instance))) {
            helper.fail("Instance creation failed");
            return;
        }
        BlockPos mobPos = instance.spawnPoints().getFirst().pos();
        Zombie before = spawnZombie(nether, mobPos);

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(player, nether, instance), "Player could not join");
        MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regeneration != null && regeneration.isInfiniteDuration(), "Player effect not applied on entry");
        MobEffectInstance speed = before.getEffect(MobEffects.SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 1, "Mob effect not applied to existing mob on first entry");
        Zombie after = spawnZombie(nether, mobPos);
        helper.assertTrue(after.hasEffect(MobEffects.SPEED), "Mob effect not applied to a mob spawned after the first entry");

        InstanceManager.leave(nether.getServer(), new EntryPoint.InstanceRef(nether.dimension(), instance.id()), player.getUUID());
        helper.assertFalse(player.hasEffect(MobEffects.REGENERATION), "Infinite player effect not removed on leave");

        before.discard();
        after.discard();
        SlotManager.release(nether, instance.slot());
        TestPlayers.finish(helper, player);
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos pos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static SmithingRecipeInput input(ItemStack scroll) {
        return new SmithingRecipeInput(new ItemStack(Items.PAPER), scroll, new ItemStack(Items.RABBIT_FOOT));
    }

    private static ScrollUpgradeRecipe recipe(ScrollEffect effect) {
        return new ScrollUpgradeRecipe(new Recipe.CommonInfo(false), Optional.of(Ingredient.of(Items.PAPER)),
                Ingredient.of(ModItems.CHALLENGE_SCROLL.get()), Optional.of(Ingredient.of(Items.RABBIT_FOOT)),
                ScrollUpgradeRecipe.OptionsPatch.NONE, List.of(effect));
    }
}

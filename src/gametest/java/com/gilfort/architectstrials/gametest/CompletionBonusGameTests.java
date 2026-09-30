package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitMarkerBlock;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.run.CompletionBonus;
import com.gilfort.architectstrials.run.RunCompletion;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-09 (completion bonus and advancement criterion).
 * <p>
 * The test datapack provides the convention bonus {@code minecraft:architectstrials/completion/the_nether/tier_2}
 * (3 diamonds), the override {@code architectstrials:gametest/bonus_override} (1 emerald) and advancements
 * {@code gametest/nether_tier_2_once}, {@code gametest/nether_twice} and {@code gametest/end_once}.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class CompletionBonusGameTests {

    private static final int PLATFORM_TIER = 2;
    private static final BlockPos EXIT_OFFSET = new BlockPos(4, 1, 8);
    private static final ResourceKey<LootTable> OVERRIDE = ResourceKey.create(Registries.LOOT_TABLE, ArchitectsTrials.id("gametest/bonus_override"));

    private CompletionBonusGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "bonus_convention_and_advancements", CompletionBonusGameTests::conventionAndAdvancements);
            register(helper, "bonus_exit_override", CompletionBonusGameTests::exitOverride);
            register(helper, "bonus_marker_carries_override", CompletionBonusGameTests::markerCarriesOverride);
            register(helper, "bonus_marker_survives_structure_save", CompletionBonusGameTests::markerSurvivesStructureSave);
            register(helper, "bonus_dev_theme_convention", CompletionBonusGameTests::devThemeConvention);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The convention loot table is granted into the inventory; the criterion matches theme, tier range and run
     * count; a missing loot table grants nothing.
     */
    private static void conventionAndAdvancements(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = createPlatform(helper, nether);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        InstanceManager.join(player, nether, instance);

        RunCompletion.complete(player, instance, Optional.empty());
        helper.assertTrue(count(player, Items.DIAMOND) == 3, "Convention bonus was not granted: " + count(player, Items.DIAMOND) + " diamonds, dimension " + player.level().dimension().identifier());
        helper.assertTrue(done(helper, player, "gametest/nether_tier_2_once"), "Criterion for one nether tier-2 run did not trigger");
        helper.assertFalse(done(helper, player, "gametest/nether_twice"), "Criterion for two runs triggered after one run");
        helper.assertFalse(done(helper, player, "gametest/end_once"), "Criterion of another theme triggered");

        RunCompletion.complete(player, instance, Optional.empty());
        helper.assertTrue(done(helper, player, "gametest/nether_twice"), "Criterion for two runs did not trigger after two runs");

        ChallengeInstance missingTier = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 99, instance.structure(),
                0, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, -1L, List.of());
        int items = player.getInventory().getContainerSize() - countEmpty(player);
        CompletionBonus.grant(player, missingTier, Optional.empty());
        helper.assertTrue(player.getInventory().getContainerSize() - countEmpty(player) == items, "A missing bonus loot table granted items");

        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, player);
    }

    /**
     * A loot table set on the used exit replaces the convention.
     */
    private static void exitOverride(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = createPlatform(helper, nether);
        BlockPos exit = instance.origin().offset(EXIT_OFFSET);
        ((ChallengeExitBlockEntity) nether.getBlockEntity(exit)).setLootTableReference(Optional.of(OVERRIDE));

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        InstanceManager.join(player, nether, instance);
        TestPlayers.teleport(player, nether, Vec3.atBottomCenterOf(exit.above()));
        RunCompletion.checkExits(nether);
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player did not complete the run");
        helper.assertTrue(count(player, Items.EMERALD) == 1, "Exit override bonus was not granted");
        helper.assertTrue(count(player, Items.DIAMOND) == 0, "Convention bonus was granted despite an override");

        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, player);
    }

    /**
     * A loot table set on an exit marker survives the marker resolution.
     */
    private static void markerCarriesOverride(GameTestHelper helper) {
        BlockPos marker = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(marker, ModBlocks.EXIT_MARKER.get().defaultBlockState());
        ((ExitMarkerBlockEntity) helper.getLevel().getBlockEntity(marker)).setLootTableReference(Optional.of(OVERRIDE));

        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, marker, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, -1L, List.of());
        ExitMarkerBlock.resolve(new MarkerContext(helper.getLevel(), dummy, helper.getLevel().getRandom()), marker);
        helper.assertTrue(helper.getLevel().getBlockEntity(marker) instanceof ChallengeExitBlockEntity exit
                && exit.lootTableReference().equals(Optional.of(OVERRIDE)), "Exit marker override was not carried over");
        helper.succeed();
    }

    /**
     * A loot table set on an exit marker is saved into a structure template and restored when the template is
     * placed again (the path editor save → pool → placement takes).
     */
    private static void markerSurvivesStructureSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(source, ModBlocks.EXIT_MARKER.get().defaultBlockState());
        ((ExitMarkerBlockEntity) level.getBlockEntity(source)).setLootTableReference(Optional.of(OVERRIDE));

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, source, new Vec3i(1, 1, 1), false, List.of());
        StructureTemplate reloaded = new StructureTemplate();
        reloaded.load(level.holderLookup(Registries.BLOCK), template.save(new CompoundTag()));

        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 1));
        reloaded.placeInWorld(level, target, target, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockEntity(target) instanceof ExitMarkerBlockEntity placed
                && placed.lootTableReference().equals(Optional.of(OVERRIDE)), "Exit marker loot table was lost in the structure");
        helper.succeed();
    }

    /**
     * The dev datapack's convention bonus for {@code architectstrials:gametest_theme} tier 1 (5 emeralds) loads.
     * The theme dimension itself does not exist on the GameTest server, so a fake instance is used.
     */
    private static void devThemeConvention(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengeInstance devInstance = new ChallengeInstance(UUID.randomUUID(), ArchitectsTrials.id("gametest_theme"), 1,
                ArchitectsTrials.id("dummy"), 0, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, -1L, List.of());
        CompletionBonus.grant(player, devInstance, Optional.empty());
        helper.assertTrue(count(player, Items.EMERALD) == 5, "Dev convention bonus was not granted: " + count(player, Items.EMERALD));
        TestPlayers.finish(helper, player);
    }

    private static ChallengeInstance createPlatform(GameTestHelper helper, ServerLevel nether) {
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(),
                PLATFORM_TIER, nether.getRandom(), InstanceManager.defaultTimeLimitTicks());
        if (result instanceof InstanceCreation.Success(ChallengeInstance instance)) {
            return instance;
        }
        throw new IllegalStateException("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
    }

    private static boolean done(GameTestHelper helper, ServerPlayer player, String advancement) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(ArchitectsTrials.id(advancement));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static int countEmpty(ServerPlayer player) {
        int empty = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }
}

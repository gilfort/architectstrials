package com.gilfort.architectstrials.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitMarkerBlock;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.editor.WorldImport;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.loot.LootEntry;
import com.gilfort.architectstrials.loot.LootGroup;
import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetups;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.TrialSpawnerMarkerResolver;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.run.CompletionBonus;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-25 (loot setups and the loot tool).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class LootSetupGameTests {

    private static final ResourceKey<LootTable> DUNGEON = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/simple_dungeon"));
    private static final ResourceKey<LootTable> BRIDGE = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/nether_bridge"));

    private LootSetupGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "loot_setup_rolls", LootSetupGameTests::rolls);
            register(helper, "loot_setup_container", LootSetupGameTests::container);
            register(helper, "loot_setup_trial_spawner_and_exit", LootSetupGameTests::trialSpawnerAndExit);
            register(helper, "loot_setup_import_weighted_rewards", LootSetupGameTests::importWeightedRewards);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    private static List<ItemStack> roll(LootSetup setup, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        setup.roll(random, (key, output) -> output.accept(new ItemStack(Items.STONE)), items::add);
        return items;
    }

    private static long count(List<ItemStack> items, Item item) {
        return items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /**
     * Groups draw at most one entry and are independent, the remainder means nothing, roll ranges apply, tables are
     * rolled, and the consolation list only comes when no group drew anything.
     */
    private static void rolls(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7L);
        LootSetup exclusive = LootSetup.EMPTY
                .withPage(0, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.IRON_INGOT), 500), true)
                        .with(LootEntry.ofItem(1, new ItemStack(Items.COPPER_INGOT), 500), true))
                .withPage(1, LootGroup.EMPTY.with(LootEntry.ofTable(0, DUNGEON, 1000).withRolls(2, 3), true))
                .withPage(LootSetup.MAX_GROUPS, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.GOLD_INGOT), 0), false));
        for (int i = 0; i < 200; i++) {
            List<ItemStack> items = roll(exclusive, random);
            long metals = count(items, Items.IRON_INGOT) + count(items, Items.COPPER_INGOT);
            helper.assertTrue(metals == 1, "A full group did not draw exactly one entry: " + items);
            long stones = count(items, Items.STONE);
            helper.assertTrue(stones >= 2 && stones <= 3, "Roll range 2–3 not respected: " + stones);
            helper.assertTrue(count(items, Items.GOLD_INGOT) == 0, "Consolation given although a group drew something");
        }

        LootSetup sometimes = LootSetup.EMPTY
                .withPage(0, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.EMERALD), 300), true))
                .withPage(LootSetup.MAX_GROUPS, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.GOLD_INGOT), 0), false)
                        .with(LootEntry.ofItem(1, new ItemStack(Items.BREAD), 0), false));
        int emeralds = 0;
        int consolations = 0;
        for (int i = 0; i < 2000; i++) {
            List<ItemStack> items = roll(sometimes, random);
            if (count(items, Items.EMERALD) > 0) {
                emeralds++;
                helper.assertTrue(count(items, Items.GOLD_INGOT) == 0, "Consolation with a drawn entry");
            } else {
                consolations++;
                helper.assertTrue(count(items, Items.GOLD_INGOT) == 1 && count(items, Items.BREAD) == 1,
                        "Consolation list not given completely: " + items);
            }
        }
        helper.assertTrue(Math.abs(emeralds - 600) < 120 && emeralds + consolations == 2000, "30 % entry drawn " + emeralds + " of 2000");

        LootGroup clamped = LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.DIAMOND), 700), true)
                .with(LootEntry.ofItem(1, new ItemStack(Items.EMERALD), 900), true);
        helper.assertTrue(clamped.totalChance() == 1000, "Group above 100 % was not clamped: " + clamped.totalChance());
        helper.succeed();
    }

    /**
     * A chest with a setup keeps its hand-placed items, gets the placeholder loot table and rolls the setup when
     * it is unpacked; the setup is copied between containers.
     */
    private static void container(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.TORCH, 5));
        LootSetup setup = LootSetup.EMPTY.withPage(0, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.DIAMOND), 1000).withRolls(3, 3), true));
        LootSetups.set(chest, false, setup);
        helper.assertTrue(LootSetups.PLACEHOLDER.equals(chest.getLootTable()), "Placeholder loot table not set");

        BlockPos copyPos = new BlockPos(3, 2, 1);
        helper.setBlock(copyPos, Blocks.BARREL);
        LootSetups.set(helper.getLevel().getBlockEntity(helper.absolutePos(copyPos)), false, LootSetups.get(chest, false));
        LootSetup copied = LootSetups.get(helper.getLevel().getBlockEntity(helper.absolutePos(copyPos)), false);
        helper.assertTrue(copied.page(0).entries().size() == 1 && copied.page(0).entries().getFirst().item().is(Items.DIAMOND)
                && copied.page(0).entries().getFirst().rollsMin() == 3, "Setup was not copied to another container");

        chest.unpackLootTable(null);
        int diamonds = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.DIAMOND)) {
                diamonds += chest.getItem(slot).getCount();
            }
        }
        helper.assertTrue(diamonds == 3, "Setup not rolled on unpacking: " + diamonds + " diamonds");
        helper.assertTrue(chest.getItem(0).is(Items.TORCH) || containsTorches(chest), "Hand-placed items were lost");
        helper.assertTrue(chest.getLootTable() == null, "Placeholder was not cleared after rolling");
        helper.succeed();
    }

    private static boolean containsTorches(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.TORCH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A trial spawner marker with setups ejects them (normal and ominous); an exit marker passes its setup to the
     * exit, which grants it as completion bonus.
     */
    private static void trialSpawnerAndExit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 5; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        BlockPos spawnerPos = new BlockPos(2, 2, 2);
        helper.setBlock(spawnerPos, ModBlocks.TRIAL_SPAWNER_MARKER.get());
        TrialSpawnerMarkerBlockEntity marker = helper.getBlockEntity(spawnerPos, TrialSpawnerMarkerBlockEntity.class);
        marker.setItem(0, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 2));
        marker.setLootSetup(false, LootSetup.EMPTY.withPage(0, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.EMERALD), 1000), true)));
        marker.setLootSetup(true, LootSetup.EMPTY.withPage(0,
                LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.NETHERITE_SCRAP), 1000), true)));
        TrialSpawnerMarkerResolver.resolve(context(level), helper.absolutePos(spawnerPos));
        TrialSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerPos, TrialSpawnerBlockEntity.class);
        List<ResourceKey<LootTable>> rewards = spawner.getTrialSpawner().normalConfig().lootTablesToEject().unwrap().stream()
                .map(Weighted::value).toList();
        helper.assertTrue(rewards.equals(List.of(LootSetups.PLACEHOLDER)), "Spawner does not eject the setup: " + rewards);

        AABB around = new AABB(helper.absolutePos(spawnerPos)).inflate(3);
        spawner.getTrialSpawner().ejectReward(level, helper.absolutePos(spawnerPos), LootSetups.PLACEHOLDER);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(item -> item.getItem().is(Items.EMERALD)),
                "Normal setup was not ejected");
        LootSetups.ejectTrialReward(level, helper.absolutePos(spawnerPos), true);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(item -> item.getItem().is(Items.NETHERITE_SCRAP)),
                "Ominous setup was not ejected");
        level.getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);

        BlockPos exitPos = new BlockPos(6, 2, 6);
        helper.setBlock(exitPos, ModBlocks.EXIT_MARKER.get());
        LootSetup bonus = LootSetup.EMPTY.withPage(0, LootGroup.EMPTY.with(LootEntry.ofItem(0, new ItemStack(Items.DIAMOND), 1000).withRolls(2, 2), true));
        helper.getBlockEntity(exitPos, ExitMarkerBlockEntity.class).setLootSetup(false, bonus);
        ExitMarkerBlock.resolve(context(level), helper.absolutePos(exitPos));
        helper.assertTrue(helper.getBlockEntity(exitPos, ChallengeExitBlockEntity.class).lootSetup(false).equals(bonus),
                "Exit marker did not pass its setup to the exit");
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        player.getInventory().clearContent();
        CompletionBonus.grant(player, bonus);
        int diamonds = player.getInventory().countItem(Items.DIAMOND);
        helper.assertTrue(diamonds == 2, "Completion bonus setup not granted: " + diamonds);
        TestPlayers.finish(helper, player);
    }

    /**
     * Importing a vanilla trial spawner with several weighted rewards creates a loot setup with matching chances
     * instead of keeping only the heaviest reward.
     */
    private static void importWeightedRewards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity spawner = helper.getBlockEntity(pos, TrialSpawnerBlockEntity.class);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        TrialSpawnerConfig config = TrialSpawnerConfig.builder()
                .spawnPotentialsDefinition(WeightedList.of(new SpawnData(entity, Optional.empty(), Optional.empty())))
                .lootTablesToEject(WeightedList.<ResourceKey<LootTable>>builder().add(DUNGEON, 3).add(BRIDGE, 7).build())
                .build();
        TrialSpawner.FullConfig fullConfig = new TrialSpawner.FullConfig(Holder.direct(config), Holder.direct(config),
                TrialSpawner.FullConfig.DEFAULT.targetCooldownLength(), TrialSpawner.FullConfig.DEFAULT.requiredPlayerRange());
        CompoundTag data = new CompoundTag();
        data.store(TrialSpawner.FullConfig.MAP_CODEC, level.registryAccess().createSerializationContext(NbtOps.INSTANCE), fullConfig);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ArchitectsTrials.LOGGER)) {
            spawner.getTrialSpawner().load(TagValueInput.create(reporter, level.registryAccess(), data));
        }

        ServerLevel target = level.getServer().getLevel(Level.END);
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(pos), helper.absolutePos(pos));
        WorldImport.Result result = WorldImport.importInto(level, box, target, 3000, 0);
        BoundingBox placed = result.placed();
        try {
            if (!(target.getBlockEntity(new BlockPos(placed.minX(), placed.minY(), placed.minZ())) instanceof TrialSpawnerMarkerBlockEntity marker)) {
                helper.fail("Trial spawner was not converted");
                return;
            }
            LootGroup group = marker.lootSetup(false).page(0);
            helper.assertTrue(group.entries().size() == 2 && group.totalChance() == 1000
                    && group.entries().stream().anyMatch(e -> e.table().equals(Optional.of(DUNGEON)) && e.chance() == 300)
                    && group.entries().stream().anyMatch(e -> e.table().equals(Optional.of(BRIDGE)) && e.chance() == 700),
                    "Weighted rewards not converted into a setup: " + group);
        } finally {
            for (Entity e : target.getEntities((Entity) null, AABB.of(placed).inflate(1), e -> !(e instanceof Player))) {
                e.discard();
            }
            BlockPos.betweenClosed(placed.minX(), placed.minY(), placed.minZ(), placed.maxX(), placed.maxY(), placed.maxZ())
                    .forEach(p -> target.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
        }
        helper.succeed();
    }

    private static MarkerContext context(ServerLevel level) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE,
                InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }
}

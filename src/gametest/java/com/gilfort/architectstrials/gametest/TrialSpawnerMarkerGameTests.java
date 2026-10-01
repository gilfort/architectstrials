package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.SpawnMarkerResolvers;
import com.gilfort.architectstrials.marker.TrialSpawnerMarkerResolver;
import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-16 (trial spawner marker).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class TrialSpawnerMarkerGameTests {

    private static final ResourceKey<LootTable> REWARD = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/simple_dungeon"));

    private TrialSpawnerMarkerGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "trial_spawner_marker_config", TrialSpawnerMarkerGameTests::config);
            register(helper, "trial_spawner_marker_never_ominous", TrialSpawnerMarkerGameTests::neverOminous);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The marker becomes a trial spawner with one spawn potential per filled row (weight = egg count, marker
     * equipment attached), total mobs = sum of eggs, the configured simultaneous mobs, the referenced reward and
     * the vanilla per-player scaling; the ominous config equals the normal one.
     */
    private static void config(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        BlockPos pos = new BlockPos(5, 1, 1);
        placeMarker(helper, pos);

        TrialSpawnerMarkerResolver.resolve(context(level, helper.absolutePos(BlockPos.ZERO)), helper.absolutePos(pos));

        helper.assertBlockPresent(Blocks.TRIAL_SPAWNER, pos);
        TrialSpawnerBlockEntity spawner = helper.getBlockEntity(pos, TrialSpawnerBlockEntity.class);
        TrialSpawnerConfig config = spawner.getTrialSpawner().normalConfig();
        helper.assertTrue(config.totalMobs() == 6.0F, "Total mobs is not the sum of all eggs: " + config.totalMobs());
        helper.assertTrue(config.simultaneousMobs() == 3.0F, "Simultaneous mobs not taken from the marker: " + config.simultaneousMobs());
        helper.assertTrue(config.totalMobsAddedPerPlayer() == TrialSpawnerConfig.DEFAULT.totalMobsAddedPerPlayer()
                && config.simultaneousMobsAddedPerPlayer() == TrialSpawnerConfig.DEFAULT.simultaneousMobsAddedPerPlayer(),
                "Vanilla per-player scaling was changed");
        List<Weighted<SpawnData>> potentials = config.spawnPotentialsDefinition().unwrap();
        helper.assertTrue(potentials.size() == 2, "Expected 2 spawn potentials, got " + potentials.size());
        Weighted<SpawnData> zombie = potentials.stream()
                .filter(p -> p.value().entityToSpawn().getStringOr("id", "").equals("minecraft:zombie")).findFirst().orElse(null);
        Weighted<SpawnData> skeleton = potentials.stream()
                .filter(p -> p.value().entityToSpawn().getStringOr("id", "").equals("minecraft:skeleton")).findFirst().orElse(null);
        helper.assertTrue(zombie != null && zombie.weight() == 4, "Zombie potential missing or weight is not 4");
        helper.assertTrue(skeleton != null && skeleton.weight() == 2, "Skeleton potential missing or weight is not 2");
        helper.assertTrue(zombie.value().entityToSpawn().getCompoundOrEmpty("NeoForgeData").contains(SpawnMarkerResolvers.EQUIPMENT_KEY),
                "Zombie potential carries no marker equipment");
        List<ResourceKey<LootTable>> rewards = config.lootTablesToEject().unwrap().stream().map(Weighted::value).toList();
        helper.assertTrue(rewards.equals(List.of(REWARD)), "Reward loot table is not the marker's: " + rewards);
        helper.assertTrue(spawner.getTrialSpawner().ominousConfig().equals(config), "Ominous config differs from the normal config");
        helper.assertTrue(TrialSpawnerMarkerResolver.isOminousBlocked(level, helper.absolutePos(pos)), "Spawner is not flagged");
        helper.succeed();
    }

    /**
     * A player with Trial Omen turns a vanilla trial spawner ominous, but never a marker-created one; the omen
     * is kept.
     */
    private static void neverOminous(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        prepare(helper);
        BlockPos markerPos = new BlockPos(5, 1, 1);
        BlockPos vanillaPos = new BlockPos(1, 1, 5);
        placeMarker(helper, markerPos);
        TrialSpawnerMarkerResolver.resolve(context(level, helper.absolutePos(BlockPos.ZERO)), helper.absolutePos(markerPos));
        helper.setBlock(vanillaPos, Blocks.TRIAL_SPAWNER);
        helper.getBlockEntity(vanillaPos, TrialSpawnerBlockEntity.class).setEntityId(EntityTypes.ZOMBIE, level.getRandom());
        // The GameTest server disables mob spawning and mock players always report creative mode: let both
        // spawners run anyway and detect creative players.
        for (BlockPos pos : List.of(vanillaPos, markerPos)) {
            TrialSpawner trialSpawner = helper.getBlockEntity(pos, TrialSpawnerBlockEntity.class).getTrialSpawner();
            trialSpawner.overridePeacefulAndMobSpawnRule();
            trialSpawner.setPlayerDetector(PlayerDetector.INCLUDING_CREATIVE_PLAYERS);
        }

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.TRIAL_OMEN, 20 * 60));

        helper.runAfterDelay(45, () -> {
            helper.assertTrue(helper.getBlockState(vanillaPos).getValue(TrialSpawnerBlock.OMINOUS),
                    "Control: vanilla trial spawner did not turn ominous (player not detected?)");
            helper.assertFalse(helper.getBlockState(markerPos).getValue(TrialSpawnerBlock.OMINOUS),
                    "Marker-created trial spawner turned ominous");
            TrialSpawnerBlockEntity spawner = helper.getBlockEntity(markerPos, TrialSpawnerBlockEntity.class);
            helper.assertTrue(spawner.getState() != TrialSpawnerState.WAITING_FOR_PLAYERS, "Marker spawner did not detect the player");
            helper.assertTrue(player.hasEffect(MobEffects.TRIAL_OMEN), "Trial Omen was removed");
            level.getEntitiesOfClass(Mob.class, new AABB(helper.absolutePos(BlockPos.ZERO))
                    .inflate(16)).forEach(Mob::discard);
            TestPlayers.finish(helper, player);
        });
    }

    private static TrialSpawnerMarkerBlockEntity placeMarker(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.TRIAL_SPAWNER_MARKER.get());
        TrialSpawnerMarkerBlockEntity marker = helper.getBlockEntity(pos, TrialSpawnerMarkerBlockEntity.class);
        marker.setItem(0, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 4));
        marker.setItem(MarkerSlot.indexOf(0, EquipmentSlot.HEAD), new ItemStack(Items.IRON_HELMET));
        marker.setItem(2 * MarkerSlot.ROW_SIZE, new ItemStack(Items.SKELETON_SPAWN_EGG, 2));
        marker.setSimultaneousMobs(3);
        marker.setLootTableReference(Optional.of(REWARD));
        return marker;
    }

    private static void prepare(GameTestHelper helper) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static MarkerContext context(ServerLevel level, BlockPos origin) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"),
                0, origin, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE, InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }
}

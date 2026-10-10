package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ExitMarkerBlockEntity;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.browser.BrowserSnapshot;
import com.gilfort.architectstrials.browser.ChallengeBrowser;
import com.gilfort.architectstrials.browser.StructureStatistics;
import com.gilfort.architectstrials.browser.StructureStats;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.editor.EditorState;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.sub.SubStructureMarkerBlockEntity;
import com.gilfort.architectstrials.sub.SubStructureSetup;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-40 (challenge browser): the snapshot of the test datapack (themes, source packs, statistics,
 * problems, "used by"), the template statistics of a captured structure and the remembered editor structure.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class BrowserGameTests {

    private static final Identifier SPAWN_PLATFORM = ArchitectsTrials.id("the_nether/tier_2/spawn_platform");
    private static final Identifier REQUIRED_PLATFORM = ArchitectsTrials.id("the_nether/tier_6/required_platform");
    private static final Identifier SUB_PARENT = ArchitectsTrials.id("the_nether/tier_12/sub_parent");
    private static final Identifier MISSING_CONTENT = ArchitectsTrials.id("gametest_validation/tier_2/missing_content");
    private static final Identifier SPAWN_ROOM = ArchitectsTrials.id("spawn_room");

    private BrowserGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "browser_snapshot", BrowserGameTests::snapshot);
            register(helper, "browser_template_statistics", BrowserGameTests::templateStatistics);
            register(helper, "browser_editor_state", BrowserGameTests::editorState);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * The snapshot lists registered and unregistered themes, read-only sources, statistics, problems and which
     * challenges use a sub structure; it survives its network codec.
     */
    private static void snapshot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BrowserSnapshot snapshot = ChallengeBrowser.build(server);

        BrowserSnapshot.ThemeEntry nether = theme(helper, snapshot, Level.NETHER.identifier());
        helper.assertTrue(nether.registered(), "The Nether theme is registered in the test datapack");
        BrowserSnapshot.ThemeEntry validation = theme(helper, snapshot, ArchitectsTrials.id("gametest_validation"));
        helper.assertFalse(validation.registered(), "gametest_validation is not in the theme list");
        helper.assertTrue(validation.problemCount() >= 2, "Unregistered theme with a broken challenge must count both problems");
        for (int i = 1; i < nether.challenges().size(); i++) {
            helper.assertTrue(nether.challenges().get(i - 1).metadata().tier() <= nether.challenges().get(i).metadata().tier(),
                    "Challenges are not sorted by tier");
        }

        BrowserSnapshot.ChallengeEntry platform = challenge(helper, nether, SPAWN_PLATFORM);
        helper.assertTrue(platform.stats().found() && platform.stats().playerSpawns() >= 1 && platform.stats().exits() >= 1,
                "Spawn platform statistics are wrong: " + platform.stats());
        helper.assertTrue(platform.problems().isEmpty(), "Clean structure reported problems: " + platform.problems());
        helper.assertFalse(platform.source().isEmpty(), "Source pack is missing");
        helper.assertFalse(platform.editable(), "Structures of the test datapack must be read-only");

        BrowserSnapshot.ChallengeEntry required = challenge(helper, nether, REQUIRED_PLATFORM);
        helper.assertTrue(required.stats().mobs().stream().anyMatch(StructureStats.MobCount::required),
                "Required mobs are not reported: " + required.stats().mobs());

        BrowserSnapshot.ChallengeEntry parent = challenge(helper, nether, SUB_PARENT);
        helper.assertTrue(parent.stats().subStructures().contains(SPAWN_ROOM), "Sub structure marker target missing: " + parent.stats());
        BrowserSnapshot.SubEntry spawnRoom = snapshot.subStructures().stream().filter(sub -> sub.id().equals(SPAWN_ROOM)).findFirst()
                .orElseThrow(() -> helper.assertionException("Sub structure spawn_room missing"));
        helper.assertTrue(spawnRoom.usedBy().contains(SUB_PARENT), "spawn_room is not used by the parent: " + spawnRoom.usedBy());
        helper.assertTrue(spawnRoom.stats().playerSpawns() == 1, "spawn_room statistics are wrong: " + spawnRoom.stats());

        BrowserSnapshot.ChallengeEntry missing = challenge(helper, validation, MISSING_CONTENT);
        helper.assertTrue(missing.problems().containsKey("missing block: missingmod:strange_block"),
                "Validation problems are missing: " + missing.problems());

        BrowserSnapshot decoded = BrowserSnapshot.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                BrowserSnapshot.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), snapshot).getOrThrow())
                .getOrThrow();
        helper.assertTrue(decoded.equals(snapshot), "Snapshot did not survive its codec");
        helper.succeed();
    }

    /**
     * Statistics of a captured structure: size, markers, required mobs, loot kinds and sub structure targets.
     */
    private static void templateStatistics(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z <= 6; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        helper.setBlock(0, 2, 0, ModBlocks.PLAYER_SPAWN_MARKER.get());
        helper.setBlock(1, 2, 0, ModBlocks.PLAYER_SPAWN_MARKER.get());
        helper.setBlock(5, 2, 0, ModBlocks.EXIT_MARKER.get());
        helper.getBlockEntity(new BlockPos(5, 2, 0), ExitMarkerBlockEntity.class).setRequiresMobs(true);
        helper.setBlock(2, 2, 2, ModBlocks.DIRECT_SPAWN_MARKER.get());
        SpawnMarkerBlockEntity direct = helper.getBlockEntity(new BlockPos(2, 2, 2), SpawnMarkerBlockEntity.class);
        direct.setItem(0, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 3));
        direct.setRequired(true);
        helper.setBlock(3, 2, 2, ModBlocks.SPAWNER_MARKER.get());
        helper.getBlockEntity(new BlockPos(3, 2, 2), SpawnMarkerBlockEntity.class).setItem(0, new ItemStack(Items.SKELETON_SPAWN_EGG, 2));
        helper.setBlock(0, 2, 3, Blocks.CHEST);
        helper.getBlockEntity(new BlockPos(0, 2, 3), ChestBlockEntity.class)
                .setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/simple_dungeon")), 0L);
        helper.setBlock(1, 2, 3, Blocks.BARREL);
        helper.getBlockEntity(new BlockPos(1, 2, 3), BarrelBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND));
        helper.setBlock(2, 2, 3, ModBlocks.SUB_STRUCTURE_MARKER.get());
        helper.getBlockEntity(new BlockPos(2, 2, 3), SubStructureMarkerBlockEntity.class).setSetup(new SubStructureSetup(
                List.of(new SubStructureSetup.Entry(ArchitectsTrials.id("row"), 50)), Optional.of(ArchitectsTrials.id("single"))));

        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 4, 6)));
        StructureStats stats = StructureStatistics.compute(level.getServer(), EditorCapture.capture(level, area).orElseThrow().template());
        helper.assertTrue(stats.sizeX() == 6 && stats.sizeY() == 2 && stats.sizeZ() == 4, "Wrong size: " + stats);
        helper.assertTrue(stats.playerSpawns() == 2 && stats.exits() == 1 && stats.exitsNeedMobs() == 1, "Wrong marker counts: " + stats);
        helper.assertTrue(stats.mobs().contains(new StructureStats.MobCount(StructureStats.MarkerKind.DIRECT,
                Identifier.withDefaultNamespace("zombie"), 3, true)), "Direct spawn zombies missing: " + stats.mobs());
        helper.assertTrue(stats.mobs().contains(new StructureStats.MobCount(StructureStats.MarkerKind.SPAWNER,
                Identifier.withDefaultNamespace("skeleton"), 2, false)), "Spawner skeletons missing: " + stats.mobs());
        helper.assertTrue(stats.loot().contains(new StructureStats.LootCount(StructureStats.LootKind.TABLE,
                Optional.of(Identifier.withDefaultNamespace("chests/simple_dungeon")), 1)), "Loot table chest missing: " + stats.loot());
        helper.assertTrue(stats.loot().contains(new StructureStats.LootCount(StructureStats.LootKind.FILLED, Optional.empty(), 1)),
                "Hand-filled barrel missing: " + stats.loot());
        helper.assertTrue(stats.subStructures().equals(List.of(ArchitectsTrials.id("row"), ArchitectsTrials.id("single"))),
                "Sub structure targets wrong: " + stats.subStructures());
        helper.succeed();
    }

    /** The editor remembers the last structure and forgets it again; the reference survives its codec. */
    private static void editorState(GameTestHelper helper) {
        EditorState state = EditorState.of(helper.getLevel().getServer());
        Optional<EditorState.StructureRef> before = state.last();
        EditorState.StructureRef ref = new EditorState.StructureRef(true, SPAWN_ROOM);
        state.setLast(Optional.of(ref));
        helper.assertTrue(state.last().equals(Optional.of(ref)), "Last structure was not remembered");
        helper.assertTrue(EditorState.StructureRef.CODEC.parse(JsonOps.INSTANCE,
                EditorState.StructureRef.CODEC.encodeStart(JsonOps.INSTANCE, ref).getOrThrow()).getOrThrow().equals(ref),
                "Structure reference did not survive its codec");
        state.setLast(Optional.empty());
        helper.assertTrue(state.last().isEmpty(), "Last structure was not forgotten");
        state.setLast(before);
        helper.succeed();
    }

    private static BrowserSnapshot.ThemeEntry theme(GameTestHelper helper, BrowserSnapshot snapshot, Identifier id) {
        return snapshot.themes().stream().filter(theme -> theme.id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException("Theme missing in snapshot: " + id));
    }

    private static BrowserSnapshot.ChallengeEntry challenge(GameTestHelper helper, BrowserSnapshot.ThemeEntry theme, Identifier id) {
        return theme.challenges().stream().filter(challenge -> challenge.id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException("Challenge missing in snapshot: " + id));
    }
}

package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.sub.SubStructureMarkerBlock;
import com.gilfort.architectstrials.sub.SubStructureMarkerBlockEntity;
import com.gilfort.architectstrials.sub.SubStructurePlacer;
import com.gilfort.architectstrials.sub.SubStructureSetup;
import com.gilfort.architectstrials.sub.SubStructures;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-32 (sub structures). The test datapack has the sub structures {@code architectstrials:row}
 * (gold, iron, diamond along +X), {@code single} (one emerald block), {@code nested} (gold plus a Sub Structure
 * Marker) and {@code spawn_room} (3 × 3 platform with a Player Spawn Marker), and Nether tier 12, a platform whose
 * Sub Structure Marker places {@code spawn_room}.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SubStructureGameTests {

    private static final Identifier ROW = ArchitectsTrials.id("row");
    private static final Identifier SINGLE = ArchitectsTrials.id("single");
    private static final Identifier NESTED = ArchitectsTrials.id("nested");

    private SubStructureGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "sub_structure_roll", SubStructureGameTests::roll);
            register(helper, "sub_structure_facing_and_offsets", SubStructureGameTests::facingAndOffsets);
            register(helper, "sub_structure_failures", SubStructureGameTests::failures);
            register(helper, "sub_structure_instance", SubStructureGameTests::instance);
            register(helper, "sub_structure_tool_and_editor", SubStructureGameTests::toolAndEditor);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Chances, fallback and nothing; the codec rejects more than 100 %; the sub structures are loaded and not part
     * of any challenge pool.
     */
    private static void roll(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        SubStructureSetup always = new SubStructureSetup(List.of(new SubStructureSetup.Entry(ROW, 100)), Optional.empty());
        SubStructureSetup fallbackOnly = new SubStructureSetup(List.of(), Optional.of(SINGLE));
        SubStructureSetup split = new SubStructureSetup(List.of(new SubStructureSetup.Entry(ROW, 30)), Optional.of(SINGLE));
        int rows = 0;
        for (int i = 0; i < 1000; i++) {
            helper.assertTrue(always.roll(random).equals(Optional.of(ROW)), "100 % entry was not rolled");
            helper.assertTrue(fallbackOnly.roll(random).equals(Optional.of(SINGLE)), "Fallback was not used");
            helper.assertTrue(SubStructureSetup.EMPTY.roll(random).isEmpty(), "Empty setup rolled something");
            Optional<Identifier> rolled = split.roll(random);
            helper.assertTrue(rolled.isPresent(), "Setup with fallback rolled nothing");
            if (rolled.get().equals(ROW)) {
                rows++;
            }
        }
        helper.assertTrue(rows > 230 && rows < 370, "30 % entry rolled " + rows + " of 1000 times");
        SubStructureSetup tooMuch = new SubStructureSetup(List.of(new SubStructureSetup.Entry(ROW, 60), new SubStructureSetup.Entry(SINGLE, 50)),
                Optional.empty());
        helper.assertTrue(SubStructureSetup.CODEC.encodeStart(JsonOps.INSTANCE, tooMuch).isError(), "Codec accepted 110 %");
        helper.assertTrue(SubStructures.get(ROW).isPresent() && SubStructures.get(ArchitectsTrials.id("spawn_room")).isPresent(),
                "Sub structures were not loaded");
        helper.succeed();
    }

    /**
     * The marker's facing rotates the sub structure (north = as built); the offset area is turned with the parent's
     * rotation; the marker is removed; nested Sub Structure Markers place nothing.
     */
    private static void facingAndOffsets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos north = helper.absolutePos(new BlockPos(1, 2, 1));
        marker(level, north, Direction.NORTH, ROW, new int[6]);
        SubStructurePlacer placer = new SubStructurePlacer(level, area(helper), RandomSource.create(1));
        helper.assertTrue(placer.placeAt(north, Rotation.NONE, Mirror.NONE), "Row facing north was not placed");
        assertRow(helper, level, north, Direction.EAST);

        BlockPos east = helper.absolutePos(new BlockPos(1, 2, 4));
        marker(level, east, Direction.EAST, ROW, new int[6]);
        helper.assertTrue(placer.placeAt(east, Rotation.NONE, Mirror.NONE), "Row facing east was not placed");
        assertRow(helper, level, east, Direction.SOUTH);

        // +X offset 4 in the editor view; the parent is turned by 90°, so the area extends to +Z in the world.
        BlockPos offsetMarker = helper.absolutePos(new BlockPos(6, 2, 1));
        marker(level, offsetMarker, Direction.NORTH, SINGLE, new int[] {0, 4, 0, 0, 0, 0});
        helper.assertTrue(placer.placeAt(offsetMarker, Rotation.CLOCKWISE_90, Mirror.NONE), "Offset sub structure was not placed");
        int found = 0;
        for (int dz = 0; dz <= 4; dz++) {
            if (level.getBlockState(offsetMarker.south(dz)).is(Blocks.EMERALD_BLOCK)) {
                found++;
            }
        }
        helper.assertTrue(found == 1, "Offset sub structure is not in the turned offset area (found " + found + ")");
        helper.assertFalse(level.getBlockState(offsetMarker.east()).is(Blocks.EMERALD_BLOCK), "Offset area was not turned with the parent");

        BlockPos nested = helper.absolutePos(new BlockPos(1, 2, 8));
        marker(level, nested, Direction.NORTH, NESTED, new int[6]);
        helper.assertTrue(placer.placeAt(nested, Rotation.NONE, Mirror.NONE), "Nested sub structure was not placed");
        helper.assertTrue(level.getBlockState(nested).is(Blocks.GOLD_BLOCK), "Nested sub structure content missing");
        helper.assertTrue(level.getBlockState(nested.east()).isAir() && !level.getBlockState(nested.east(2)).is(Blocks.EMERALD_BLOCK),
                "Nested Sub Structure Marker was not removed or placed something");
        helper.succeed();
    }

    /**
     * Overwriting a Player Spawn or Exit Marker or leaving the allowed area fails, with one new roll; other blocks
     * and markers are overwritten; a second failure places nothing.
     */
    private static void failures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        marker(level, pos, Direction.NORTH, ROW, new int[6]);
        level.setBlock(pos.east(), ModBlocks.PLAYER_SPAWN_MARKER.get().defaultBlockState(), 3);
        SubStructurePlacer placer = new SubStructurePlacer(level, area(helper), RandomSource.create(2));
        helper.assertFalse(placer.placeAt(pos, Rotation.NONE, Mirror.NONE), "Row overwrote a Player Spawn Marker");
        helper.assertTrue(placer.failures().size() == 2, "Expected two failed attempts: " + placer.failures());
        helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.east()).is(ModBlocks.PLAYER_SPAWN_MARKER.get())
                && !level.getBlockState(pos.east(2)).is(Blocks.DIAMOND_BLOCK), "Failed generation placed something or kept the marker");

        BlockPos exit = helper.absolutePos(new BlockPos(1, 2, 4));
        marker(level, exit, Direction.NORTH, ROW, new int[6]);
        level.setBlock(exit.east(2), ModBlocks.EXIT_MARKER.get().defaultBlockState(), 3);
        helper.assertFalse(placer.placeAt(exit, Rotation.NONE, Mirror.NONE), "Row overwrote an Exit Marker");

        BlockPos other = helper.absolutePos(new BlockPos(1, 2, 7));
        marker(level, other, Direction.NORTH, ROW, new int[6]);
        level.setBlock(other.east(), ModBlocks.DIRECT_SPAWN_MARKER.get().defaultBlockState(), 3);
        level.setBlock(other.east(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(placer.placeAt(other, Rotation.NONE, Mirror.NONE), "Row did not overwrite another marker and stone");
        assertRow(helper, level, other, Direction.EAST);

        BlockPos edge = helper.absolutePos(new BlockPos(1, 2, 10));
        marker(level, edge, Direction.NORTH, ROW, new int[6]);
        SubStructurePlacer tight = new SubStructurePlacer(level, new BoundingBox(edge), RandomSource.create(3));
        helper.assertFalse(tight.placeAt(edge, Rotation.NONE, Mirror.NONE), "Row left the allowed area");
        helper.assertTrue(tight.failures().stream().allMatch(failure -> failure.contains("slot area")), "Wrong failure: " + tight.failures());
        marker(level, edge, Direction.NORTH, SINGLE, new int[6]);
        helper.assertTrue(tight.placeAt(edge, Rotation.NONE, Mirror.NONE), "Single block did not fit the allowed area");

        // Row 50 % with the single block as fallback, next to a spawn marker: the row always fails, the single
        // block is placed when the first or the retried roll gives the fallback.
        int singles = 0;
        int nothing = 0;
        for (int seed = 0; seed < 40; seed++) {
            BlockPos retry = helper.absolutePos(new BlockPos(5, 2, 1));
            level.setBlock(retry.east(), ModBlocks.PLAYER_SPAWN_MARKER.get().defaultBlockState(), 3);
            level.setBlock(retry, ModBlocks.SUB_STRUCTURE_MARKER.get().defaultBlockState(), 3);
            ((SubStructureMarkerBlockEntity) level.getBlockEntity(retry)).setSetup(
                    new SubStructureSetup(List.of(new SubStructureSetup.Entry(ROW, 50)), Optional.of(SINGLE)));
            boolean placed = new SubStructurePlacer(level, area(helper), RandomSource.create(seed)).placeAt(retry, Rotation.NONE, Mirror.NONE);
            helper.assertFalse(level.getBlockState(retry).is(Blocks.GOLD_BLOCK), "Row was placed over the spawn marker");
            if (placed) {
                helper.assertTrue(level.getBlockState(retry).is(Blocks.EMERALD_BLOCK), "Fallback was not placed");
                singles++;
            } else {
                nothing++;
            }
            level.setBlock(retry, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.assertTrue(singles > 0 && nothing > 0, "Retry outcomes not mixed: " + singles + " placed, " + nothing + " nothing");
        helper.succeed();
    }

    /**
     * On instance creation the parent's Sub Structure Marker places the sub structure; its Player Spawn Marker is
     * registered as an additional spawn point; the marker is gone.
     */
    private static void instance(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), 12,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance created))) {
            helper.fail("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
            return;
        }
        ChallengeInstance instance = InstanceManager.data(nether).get(created.id()).orElseThrow();
        helper.assertTrue(instance.spawnPoints().size() == 2, "Spawn of the sub structure was not registered: " + instance.spawnPoints());
        BlockPos origin = instance.origin();
        helper.assertTrue(nether.getBlockState(origin.offset(0, 1, 0)).is(Blocks.STONE_BRICKS)
                && nether.getBlockState(origin.offset(2, 1, 2)).is(Blocks.STONE_BRICKS), "Sub structure was not placed at the marker");
        helper.assertFalse(nether.getBlockState(origin.offset(1, 2, 1)).is(ModBlocks.PLAYER_SPAWN_MARKER.get()),
                "Player Spawn Marker of the sub structure was not resolved");
        InstanceManager.close(nether, instance.id());
        helper.succeed();
    }

    /**
     * The tool copies a marker's setup (shift + right click); the editor rejects sub structures with Sub Structure
     * Markers and accepts sub structures without spawn and exit.
     */
    private static void toolAndEditor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 1));
        SubStructureSetup setup = new SubStructureSetup(List.of(new SubStructureSetup.Entry(ROW, 40)), Optional.of(SINGLE));
        marker(level, pos, Direction.NORTH, ROW, new int[6]);
        ((SubStructureMarkerBlockEntity) level.getBlockEntity(pos)).setSetup(setup);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        ItemStack tool = new ItemStack(ModItems.SUB_STRUCTURE_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.setShiftKeyDown(true);
        tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(setup.equals(player.getMainHandItem().get(ModDataComponents.SUB_STRUCTURE_CLIPBOARD.get())),
                "Tool did not copy the setup");
        player.setShiftKeyDown(false);

        BlockPos editorOrigin = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(editorOrigin, Blocks.STONE.defaultBlockState(), 3);
        BoundingBox editorArea = new BoundingBox(editorOrigin).inflatedBy(1);
        EditorCapture.Captured plain = EditorCapture.capture(level, editorArea).orElseThrow();
        helper.assertTrue(EditorCapture.validateSub(level, plain).valid(), "Sub structure without spawn / exit was rejected");
        level.setBlock(editorOrigin.above(), ModBlocks.SUB_STRUCTURE_MARKER.get().defaultBlockState(), 3);
        EditorCapture.Captured withMarker = EditorCapture.capture(level, editorArea).orElseThrow();
        helper.assertFalse(EditorCapture.validateSub(level, withMarker).valid(), "Sub structure with a Sub Structure Marker was accepted");
        TestPlayers.finish(helper, player);
    }

    private static BoundingBox area(GameTestHelper helper) {
        return new BoundingBox(helper.absolutePos(BlockPos.ZERO)).inflatedBy(24);
    }

    private static void marker(ServerLevel level, BlockPos pos, Direction facing, Identifier structure, int[] offsets) {
        level.setBlock(pos, ModBlocks.SUB_STRUCTURE_MARKER.get().defaultBlockState().setValue(SubStructureMarkerBlock.FACING, facing), 3);
        SubStructureMarkerBlockEntity marker = (SubStructureMarkerBlockEntity) level.getBlockEntity(pos);
        marker.setSetup(new SubStructureSetup(List.of(new SubStructureSetup.Entry(structure, 100)), Optional.empty()));
        marker.setOffsets(offsets);
    }

    private static void assertRow(GameTestHelper helper, ServerLevel level, BlockPos origin, Direction along) {
        helper.assertTrue(level.getBlockState(origin).is(Blocks.GOLD_BLOCK)
                && level.getBlockState(origin.relative(along)).is(Blocks.IRON_BLOCK)
                && level.getBlockState(origin.relative(along, 2)).is(Blocks.DIAMOND_BLOCK), "Row is not placed along " + along + " at " + origin);
    }
}

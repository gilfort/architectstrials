package com.gilfort.architectstrials.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.structure.PaintingPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for {@link PaintingPlacement}: paintings with even width or height keep their position when a
 * captured structure is placed, also rotated and mirrored.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class PaintingPlacementGameTests {

    private static final List<ResourceKey<PaintingVariant>> VARIANTS = List.of(PaintingVariants.WANDERER, PaintingVariants.BUST,
            PaintingVariants.FIGHTERS);

    private PaintingPlacementGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("painting_placement_keeps_position")),
                (Consumer<GameTestHelper>) PaintingPlacementGameTests::keepsPosition));
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("painting_placement_all_sizes")),
                (Consumer<GameTestHelper>) PaintingPlacementGameTests::allSizesAndFacings));
    }

    private static void keepsPosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 16; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 0; z <= 16; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = 0; x <= 9; x++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
            }
        }
        List<BlockPos> anchors = List.of(new BlockPos(0, 3, 1), new BlockPos(2, 3, 1), new BlockPos(6, 3, 1));
        for (int i = 0; i < anchors.size(); i++) {
            Painting painting = new Painting(level, helper.absolutePos(anchors.get(i)), Direction.SOUTH,
                    level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(VARIANTS.get(i)));
            level.addFreshEntity(painting);
        }
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(9, 6, 1)));
        List<Painting> originals = paintings(level, area);
        helper.assertTrue(originals.size() == 3, "Expected 3 hung paintings, got " + originals.size());
        StructureTemplate template = EditorCapture.capture(level, area).orElseThrow().template();
        BlockPos captureOrigin = helper.absolutePos(new BlockPos(0, 2, 0));

        BlockPos straight = helper.absolutePos(new BlockPos(0, 2, 4));
        place(level, template, straight, Rotation.NONE, Mirror.NONE);
        BlockPos offset = straight.subtract(captureOrigin);
        List<Painting> copies = paintings(level, area.moved(offset.getX(), offset.getY(), offset.getZ()));
        for (Painting original : originals) {
            AABB expected = original.getBoundingBox().move(offset.getX(), offset.getY(), offset.getZ());
            helper.assertTrue(copies.stream().anyMatch(copy -> same(copy.getBoundingBox(), expected)),
                    "Painting " + original.getVariant().getRegisteredName() + " moved: expected " + expected + ", got "
                            + copies.stream().map(Painting::getBoundingBox).toList());
        }

        assertAttached(helper, level, template, helper.absolutePos(new BlockPos(16, 2, 6)), Rotation.CLOCKWISE_90, Mirror.NONE);
        assertAttached(helper, level, template, helper.absolutePos(new BlockPos(0, 2, 12)), Rotation.NONE, Mirror.LEFT_RIGHT);

        level.getEntitiesOfClass(Painting.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20)).forEach(Painting::discard);
        helper.succeed();
    }

    /**
     * Every painting size in every facing keeps its exact position when captured and placed again unrotated.
     */
    private static void allSizesAndFacings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Holder<PaintingVariant>> bySize = new LinkedHashMap<>();
        level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).listElements()
                .forEach(variant -> bySize.putIfAbsent(variant.value().width() + "x" + variant.value().height(), variant));
        List<String> failures = new ArrayList<>();
        BlockPos center = new BlockPos(6, 6, 6);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Map.Entry<String, Holder<PaintingVariant>> size : bySize.entrySet()) {
                for (int x = 0; x <= 12; x++) {
                    for (int y = 1; y <= 12; y++) {
                        for (int z = 0; z <= 12; z++) {
                            helper.setBlock(x, y, z, Blocks.AIR);
                        }
                    }
                }
                BlockPos wall = center.relative(facing.getOpposite());
                for (int a = -5; a <= 5; a++) {
                    for (int y = -5; y <= 5; y++) {
                        helper.setBlock(wall.relative(facing.getClockWise(), a).above(y), Blocks.STONE);
                    }
                }
                Painting painting = new Painting(level, helper.absolutePos(center), facing, size.getValue());
                level.addFreshEntity(painting);
                AABB before = painting.getBoundingBox();
                BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(12, 12, 12)));
                EditorCapture.Captured captured = EditorCapture.capture(level, area).orElseThrow();
                painting.discard();
                place(level, captured.template(), captured.origin(), Rotation.NONE, Mirror.NONE);
                List<Painting> placed = paintings(level, area);
                if (placed.size() != 1 || !same(placed.getFirst().getBoundingBox(), before)) {
                    failures.add(size.getKey() + " " + facing + ": " + before + " -> " + placed.stream().map(Painting::getBoundingBox).toList());
                }
                placed.forEach(Painting::discard);
            }
        }
        helper.assertTrue(failures.isEmpty(), "Paintings moved: " + failures);
        helper.succeed();
    }

    private static void assertAttached(GameTestHelper helper, ServerLevel level, StructureTemplate template, BlockPos origin,
            Rotation rotation, Mirror mirror) {
        StructurePlaceSettings settings = place(level, template, origin, rotation, mirror);
        BoundingBox box = template.getBoundingBox(settings, origin);
        List<Painting> placed = paintings(level, box);
        Set<String> variants = placed.stream().map(p -> p.getVariant().getRegisteredName()).collect(Collectors.toSet());
        helper.assertTrue(placed.size() == 3 && variants.size() == 3, rotation + "/" + mirror + ": expected 3 paintings, got " + variants);
        for (Painting painting : placed) {
            helper.assertTrue(painting.survives(), rotation + "/" + mirror + ": painting " + painting.getVariant().getRegisteredName()
                    + " is not attached to the wall at " + painting.getBoundingBox());
        }
    }

    private static StructurePlaceSettings place(ServerLevel level, StructureTemplate template, BlockPos origin, Rotation rotation, Mirror mirror) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        PaintingPlacement.place(level, template, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return settings;
    }

    private static boolean same(AABB a, AABB b) {
        double epsilon = 1.0E-4;
        return Math.abs(a.minX - b.minX) < epsilon && Math.abs(a.minY - b.minY) < epsilon && Math.abs(a.minZ - b.minZ) < epsilon
                && Math.abs(a.maxX - b.maxX) < epsilon && Math.abs(a.maxY - b.maxY) < epsilon && Math.abs(a.maxZ - b.maxZ) < epsilon;
    }

    private static List<Painting> paintings(ServerLevel level, BoundingBox box) {
        return level.getEntitiesOfClass(Painting.class, AABB.of(box));
    }
}

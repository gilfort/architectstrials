package com.gilfort.architectstrials.structure;

import java.util.ArrayList;
import java.util.List;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Places structure templates without moving paintings.
 * <p>
 * Vanilla places template entities by moving them to their stored center. A painting derives its anchor block
 * from that center, and for an even width or height the center lies exactly on a block boundary, so the painting
 * ends up one block off (and may fall off the wall). Vanilla's own structures never contain painting entities,
 * and the placement settings offer no option for it. Therefore all mod placements go through {@link #place}:
 * paintings are taken out of the template, the rest is placed by vanilla, and the paintings are added afterwards
 * at the anchor that matches their transformed center (rotation and mirroring included).
 */
public final class PaintingPlacement {

    private static final String PAINTING_ID = "minecraft:painting";
    private static final double WALL_SHIFT = 0.46875;

    private PaintingPlacement() {
    }

    /**
     * Places a template like {@link StructureTemplate#placeInWorld}, with correctly anchored paintings.
     *
     * @param level    the level
     * @param template the template
     * @param origin   the placement origin
     * @param settings the placement settings
     * @param random   the random source
     * @param flags    the block update flags
     */
    public static void place(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            RandomSource random, int flags) {
        CompoundTag data = template.save(new CompoundTag());
        ListTag others = new ListTag();
        List<CompoundTag> paintings = new ArrayList<>();
        for (Tag entry : data.getListOrEmpty(StructureTemplate.ENTITIES_TAG)) {
            if (entry instanceof CompoundTag info) {
                boolean painting = PAINTING_ID.equals(info.getCompoundOrEmpty(StructureTemplate.ENTITY_TAG_NBT).getStringOr("id", ""));
                if (painting) {
                    paintings.add(info);
                } else {
                    others.add(info);
                }
            }
        }
        if (paintings.isEmpty()) {
            template.placeInWorld(level, origin, origin, settings, random, flags);
            return;
        }
        data.put(StructureTemplate.ENTITIES_TAG, others);
        StructureTemplate withoutPaintings = new StructureTemplate();
        withoutPaintings.load(level.holderLookup(Registries.BLOCK), data);
        withoutPaintings.placeInWorld(level, origin, origin, settings, random, flags);
        if (!settings.isIgnoreEntities()) {
            paintings.forEach(info -> addPainting(level, info, origin, settings));
        }
    }

    /**
     * Checks whether a template entity is a painting.
     *
     * @param info the template entity
     * @return {@code true} for paintings
     */
    public static boolean isPainting(StructureTemplate.StructureEntityInfo info) {
        return PAINTING_ID.equals(info.nbt.getStringOr("id", ""));
    }

    /**
     * Adds a painting of a template at the anchor that matches its transformed center, like {@link #place} does
     * for the paintings of a whole template.
     *
     * @param level    the level
     * @param info     the painting entity of the template
     * @param origin   the placement origin
     * @param settings the placement settings
     */
    public static void addPainting(ServerLevel level, StructureTemplate.StructureEntityInfo info, BlockPos origin, StructurePlaceSettings settings) {
        addPainting(level, info.pos, info.nbt, origin, settings);
    }

    private static void addPainting(ServerLevel level, CompoundTag info, BlockPos origin, StructurePlaceSettings settings) {
        ListTag pos = info.getListOrEmpty(StructureTemplate.ENTITY_TAG_POS);
        addPainting(level, new Vec3(pos.getDoubleOr(0, 0.0), pos.getDoubleOr(1, 0.0), pos.getDoubleOr(2, 0.0)),
                info.getCompoundOrEmpty(StructureTemplate.ENTITY_TAG_NBT), origin, settings);
    }

    private static void addPainting(ServerLevel level, Vec3 localPos, CompoundTag entityNbt, BlockPos origin, StructurePlaceSettings settings) {
        Vec3 center = StructureTemplate.transformedVec3d(settings, localPos).add(Vec3.atLowerCornerOf(origin));
        CompoundTag nbt = entityNbt.copy();
        nbt.remove("UUID");
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "painting at " + center, ArchitectsTrials.LOGGER)) {
            EntityType.create(TagValueInput.create(reporter, level.registryAccess(), nbt), level,
                    new EntitySpawnRequest(EntitySpawnReason.STRUCTURE, false)).ifPresent(entity -> {
                        if (entity instanceof Painting painting) {
                            painting.rotate(settings.getRotation());
                            painting.mirror(settings.getMirror());
                            anchorAt(painting, center);
                            level.addFreshEntity(painting);
                        }
                    });
        }
    }

    /**
     * Moves a painting to the anchor block that matches the given center: the inverse of
     * {@code Painting#calculateBoundingBox}.
     *
     * @param painting the painting, already rotated/mirrored
     * @param center   the painting's intended center position
     */
    public static void anchorAt(Painting painting, Vec3 center) {
        Direction facing = painting.getDirection();
        PaintingVariant variant = painting.getVariant().value();
        Vec3 anchorCenter = center
                .relative(facing, WALL_SHIFT)
                .relative(facing.getCounterClockWise(), -offset(variant.width()))
                .relative(Direction.UP, -offset(variant.height()));
        BlockPos anchor = BlockPos.containing(anchorCenter);
        painting.setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
    }

    private static double offset(int size) {
        return size % 2 == 0 ? 0.5 : 0.0;
    }

}

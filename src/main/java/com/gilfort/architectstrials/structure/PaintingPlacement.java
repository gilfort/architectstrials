package com.gilfort.architectstrials.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.phys.Vec3;

/**
 * Fixes the position of paintings placed from structure templates.
 * <p>
 * Vanilla places template entities by moving them to their stored center. A painting derives its anchor block
 * from that center, and for even widths or heights the center lies exactly on a block boundary, so the painting
 * ends up one block off (and may fall off the wall). The correct anchor is recomputed from the center, the
 * facing and the variant size — the inverse of {@code Painting#calculateBoundingBox}. Works for rotated and
 * mirrored placements, since the center is transformed correctly by the template.
 */
public final class PaintingPlacement {

    private static final double WALL_SHIFT = 0.46875;

    private PaintingPlacement() {
    }

    /**
     * Moves a painting to the anchor block that matches the given center.
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

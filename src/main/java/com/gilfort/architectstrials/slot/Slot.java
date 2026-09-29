package com.gilfort.architectstrials.slot;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A slot in the offset grid of a theme dimension. Each slot hosts at most one challenge instance.
 *
 * @param index   the slot index; lower indices are closer to the origin
 * @param centerX the X coordinate of the slot center
 * @param centerZ the Z coordinate of the slot center
 */
public record Slot(int index, int centerX, int centerZ) {

    /** Maximum structure footprint (X and Z) that fits into a slot. */
    public static final int MAX_STRUCTURE_SIZE = 128;

    /** Extra margin around the maximum footprint that is cleared as well, catching overhanging content. */
    public static final int CLEAR_MARGIN = 16;

    /** Half the width of the area owned by a slot. */
    public static final int HALF_EXTENT = MAX_STRUCTURE_SIZE / 2 + CLEAR_MARGIN;

    /**
     * Returns the area owned by this slot: the maximum structure footprint plus margin, over the full given
     * height range.
     *
     * @param minY the lowest Y coordinate (inclusive)
     * @param maxY the highest Y coordinate (inclusive)
     * @return the owned area
     */
    public BoundingBox area(int minY, int maxY) {
        return new BoundingBox(this.centerX - HALF_EXTENT, minY, this.centerZ - HALF_EXTENT,
                this.centerX + HALF_EXTENT - 1, maxY, this.centerZ + HALF_EXTENT - 1);
    }
}

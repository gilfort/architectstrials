package com.gilfort.architectstrials.slot;

/**
 * Maps slot indices to grid cells along a square spiral around the origin, so lower indices are always
 * closer to the origin (index 0 = origin, 1–8 = first ring, 9–24 = second ring, …).
 */
public final class SlotGrid {

    /** Largest ring whose indices still fit into an {@code int}. */
    private static final int MAX_RING = 23_000;

    private SlotGrid() {
    }

    /**
     * Creates the slot for an index at the given spacing.
     *
     * @param index   the slot index ({@code >= 0})
     * @param spacing the distance between neighbouring slot centers in blocks
     * @return the slot
     */
    public static Slot slot(int index, int spacing) {
        int[] cell = cell(index);
        return new Slot(index, cell[0] * spacing, cell[1] * spacing);
    }

    /**
     * Computes the grid cell of a slot index.
     *
     * @param index the slot index ({@code >= 0})
     * @return the cell as {@code {x, z}}
     * @throws IllegalArgumentException if the index is negative
     */
    public static int[] cell(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Slot index must not be negative: " + index);
        }
        if (index == 0) {
            return new int[] {0, 0};
        }
        int ring = 1;
        while ((long) (2 * ring + 1) * (2 * ring + 1) <= index) {
            ring++;
        }
        int offset = index - (2 * ring - 1) * (2 * ring - 1);
        int side = 2 * ring;
        int position = offset % side;
        return switch (offset / side) {
            case 0 -> new int[] {ring, -ring + 1 + position};
            case 1 -> new int[] {ring - 1 - position, ring};
            case 2 -> new int[] {-ring, ring - 1 - position};
            default -> new int[] {-ring + 1 + position, -ring};
        };
    }

    /**
     * Computes the slot index of a grid cell; the inverse of {@link #cell(int)}.
     *
     * @param x the cell X
     * @param z the cell Z
     * @return the slot index, or {@code -1} if the cell is too far out to have an {@code int} index
     */
    public static int index(int x, int z) {
        int ring = Math.max(Math.abs(x), Math.abs(z));
        if (ring == 0) {
            return 0;
        }
        if (ring > MAX_RING) {
            return -1;
        }
        int base = (2 * ring - 1) * (2 * ring - 1);
        int side = 2 * ring;
        if (x == ring && z > -ring) {
            return base + z + ring - 1;
        }
        if (z == ring && x < ring) {
            return base + side + ring - 1 - x;
        }
        if (x == -ring && z < ring) {
            return base + 2 * side + ring - 1 - z;
        }
        return base + 3 * side + x + ring - 1;
    }
}

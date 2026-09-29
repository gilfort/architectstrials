package com.gilfort.architectstrials.slot;

/**
 * Maps slot indices to grid cells along a square spiral around the origin, so lower indices are always
 * closer to the origin (index 0 = origin, 1–8 = first ring, 9–24 = second ring, …).
 */
public final class SlotGrid {

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
}

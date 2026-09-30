package com.gilfort.architectstrials.slot;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent slot occupancy of one dimension, stored in that dimension's data folder.
 * <p>
 * A slot is either free, <em>occupied</em> (hosts an instance) or <em>clearing</em> (released, content is
 * being removed; not allocatable until done).
 */
public final class SlotData extends SavedData {

    /** Spacing marker for dimensions that never allocated a slot. */
    static final int UNSET_SPACING = 0;

    private static final Codec<SlotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("spacing", UNSET_SPACING).forGetter(data -> data.spacing),
            Codec.INT.listOf().optionalFieldOf("occupied", List.of()).forGetter(data -> List.copyOf(data.occupied)),
            Codec.INT.listOf().optionalFieldOf("clearing", List.of()).forGetter(data -> List.copyOf(data.clearing))
    ).apply(instance, SlotData::new));

    /** Saved data type; stored as {@code data/architectstrials/slots.dat} per dimension. */
    public static final SavedDataType<SlotData> TYPE = new SavedDataType<>(ArchitectsTrials.id("slots"), SlotData::new, CODEC);

    private int spacing;
    private final Set<Integer> occupied;
    private final Set<Integer> clearing;

    /** Creates empty slot data. */
    public SlotData() {
        this(UNSET_SPACING, List.of(), List.of());
    }

    private SlotData(int spacing, List<Integer> occupied, List<Integer> clearing) {
        this.spacing = spacing;
        this.occupied = new TreeSet<>(occupied);
        this.clearing = new TreeSet<>(clearing);
    }

    /**
     * Returns the spacing this dimension's slots were laid out with, adopting {@code configured} if no slot
     * is occupied or clearing. Keeps existing instances in place when the config changes.
     *
     * @param configured the currently configured spacing
     * @return the effective spacing
     */
    int effectiveSpacing(int configured) {
        if (this.spacing != configured && this.occupied.isEmpty() && this.clearing.isEmpty()) {
            this.spacing = configured;
            this.setDirty();
        }
        return this.spacing;
    }

    /**
     * Returns the occupied slot indices in ascending order.
     *
     * @return a read-only view of the occupied indices
     */
    public Set<Integer> occupied() {
        return Collections.unmodifiableSet(this.occupied);
    }

    /**
     * Returns the indices of slots currently being cleared, in ascending order.
     *
     * @return a read-only view of the clearing indices
     */
    public Set<Integer> clearing() {
        return Collections.unmodifiableSet(this.clearing);
    }

    /**
     * Returns the lowest index that is neither occupied nor clearing.
     *
     * @return the lowest free index
     */
    int lowestFreeIndex() {
        int index = 0;
        while (this.occupied.contains(index) || this.clearing.contains(index)) {
            index++;
        }
        return index;
    }

    void markOccupied(int index) {
        this.occupied.add(index);
        this.setDirty();
    }

    boolean markClearing(int index) {
        if (!this.occupied.remove(index)) {
            return false;
        }
        this.clearing.add(index);
        this.setDirty();
        return true;
    }

    void markFree(int index) {
        this.clearing.remove(index);
        this.setDirty();
    }
}

package com.gilfort.architectstrials.slot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Allocates and releases challenge slots in the offset grid of a dimension.
 * <p>
 * The logic is dimension-agnostic: callers decide which level to use. Released slots are cleared over
 * several ticks and only become allocatable again afterwards. Occupancy survives server restarts; clearing
 * that was interrupted by a restart is resumed on the next start.
 */
public final class SlotManager {

    /** Maximum number of non-empty chunk sections (16³ blocks each) cleared per server tick. */
    private static final int SECTIONS_PER_TICK = 16;

    private static final Deque<AreaClearTask> CLEAR_QUEUE = new ArrayDeque<>();

    private SlotManager() {
    }

    /**
     * Returns the persistent slot data of a level.
     *
     * @param level the level
     * @return the slot data, created on first access
     */
    public static SlotData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SlotData.TYPE);
    }

    /**
     * Returns the slot with the given index in a level, using that level's effective spacing.
     *
     * @param level the level
     * @param index the slot index
     * @return the slot
     */
    public static Slot slot(ServerLevel level, int index) {
        return SlotGrid.slot(index, data(level).effectiveSpacing(ArchitectsTrialsConfig.SLOT_SPACING.getAsInt()));
    }

    /**
     * Computes the slot whose area contains a horizontal position, using the level's effective spacing.
     *
     * @param level the level
     * @param x     the block X
     * @param z     the block Z
     * @return the slot index, or {@code -1} if the position lies between slot areas
     */
    public static int indexAt(ServerLevel level, int x, int z) {
        int spacing = data(level).effectiveSpacing(ArchitectsTrialsConfig.SLOT_SPACING.getAsInt());
        int index = SlotGrid.index(Math.floorDiv(x + spacing / 2, spacing), Math.floorDiv(z + spacing / 2, spacing));
        if (index < 0) {
            return -1;
        }
        Slot slot = SlotGrid.slot(index, spacing);
        boolean inside = x >= slot.centerX() - Slot.HALF_EXTENT && x < slot.centerX() + Slot.HALF_EXTENT
                && z >= slot.centerZ() - Slot.HALF_EXTENT && z < slot.centerZ() + Slot.HALF_EXTENT;
        return inside ? index : -1;
    }

    /**
     * Allocates the free slot closest to the origin.
     *
     * @param level the level to allocate in
     * @return the allocated slot, or empty if {@code maxConcurrentInstances} is reached
     */
    public static Optional<Slot> allocate(ServerLevel level) {
        SlotData data = data(level);
        int limit = ArchitectsTrialsConfig.MAX_CONCURRENT_INSTANCES.getAsInt();
        if (limit > 0 && data.occupied().size() >= limit) {
            return Optional.empty();
        }
        int index = data.lowestFreeIndex();
        Slot slot = slot(level, index);
        data.markOccupied(index);
        return Optional.of(slot);
    }

    /**
     * Releases an occupied slot and schedules its clearing.
     *
     * @param level the level containing the slot
     * @param index the slot index
     * @return {@code true} if the slot was occupied and is now being cleared
     */
    public static boolean release(ServerLevel level, int index) {
        if (!data(level).markClearing(index)) {
            return false;
        }
        queueSlotClearing(level, index);
        return true;
    }

    /**
     * Schedules clearing of an arbitrary area, processed in the same tick budget as released slots.
     *
     * @param level  the level containing the area
     * @param area   the area to clear (blocks, block entities and non-player entities)
     * @param onDone called with the level once the area is empty
     */
    public static void clearArea(ServerLevel level, BoundingBox area, Consumer<ServerLevel> onDone) {
        CLEAR_QUEUE.add(new AreaClearTask(level, area, onDone));
    }

    private static void queueSlotClearing(ServerLevel level, int index) {
        Slot slot = slot(level, index);
        clearArea(level, slot.area(level.getMinY(), level.getMaxY()), clearedLevel -> {
            data(clearedLevel).markFree(index);
            ArchitectsTrials.LOGGER.debug("Cleared slot {} in {}", index, clearedLevel.dimension().identifier());
        });
    }

    /**
     * Checks whether a slot is currently being cleared.
     *
     * @param level the level containing the slot
     * @param index the slot index
     * @return {@code true} while the slot is being cleared
     */
    public static boolean isClearing(ServerLevel level, int index) {
        return data(level).clearing().contains(index);
    }

    /**
     * Advances the clearing of released slots and other areas. Called once per server tick.
     *
     * @param server the server
     */
    static void tick(MinecraftServer server) {
        AreaClearTask task = CLEAR_QUEUE.peek();
        if (task == null) {
            return;
        }
        ServerLevel level = server.getLevel(task.dimension());
        if (level == null) {
            CLEAR_QUEUE.poll();
            return;
        }
        task.step(level, SECTIONS_PER_TICK);
        if (task.isDone()) {
            CLEAR_QUEUE.poll();
            task.onDone().accept(level);
        }
    }

    /**
     * Re-queues slots whose clearing was interrupted by a server stop.
     *
     * @param server the server
     */
    static void resume(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (int index : data(level).clearing()) {
                queueSlotClearing(level, index);
            }
        }
    }

    /**
     * Drops all pending clear tasks. Called when the server stops; the persisted clearing state is resumed
     * on the next start.
     */
    static void reset() {
        CLEAR_QUEUE.clear();
    }
}

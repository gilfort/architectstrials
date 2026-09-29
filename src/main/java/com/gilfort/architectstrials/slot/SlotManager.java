package com.gilfort.architectstrials.slot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

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

    private static final Deque<SlotClearTask> CLEAR_QUEUE = new ArrayDeque<>();

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
        CLEAR_QUEUE.add(new SlotClearTask(level, slot(level, index)));
        return true;
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
     * Advances the clearing of released slots. Called once per server tick.
     *
     * @param server the server
     */
    static void tick(MinecraftServer server) {
        SlotClearTask task = CLEAR_QUEUE.peek();
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
            data(level).markFree(task.index());
            ArchitectsTrials.LOGGER.debug("Cleared slot {} in {}", task.index(), task.dimension().identifier());
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
                CLEAR_QUEUE.add(new SlotClearTask(level, slot(level, index)));
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

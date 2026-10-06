package com.gilfort.architectstrials.instance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Queue of structure placements of new instances, processed one at a time with a fixed per-tick budget (US-28).
 * <p>
 * Placements are kept in memory only. Instances whose placement was interrupted by a server stop are discarded on
 * the next start and their slot is cleared, so no half-built instance can ever be entered.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class InstancePlacements {

    private static final Deque<PlacementTask> QUEUE = new ArrayDeque<>();

    private static boolean immediate;

    private InstancePlacements() {
    }

    /**
     * Makes new placements complete within {@link InstanceManager#create} instead of over several ticks. Meant
     * for GameTests, which inspect the placed structure right after creating an instance.
     *
     * @param value {@code true} to place immediately
     */
    public static void setImmediate(boolean value) {
        immediate = value;
    }

    /** @return {@code true} if new placements complete immediately */
    public static boolean isImmediate() {
        return immediate;
    }

    /**
     * Starts the placement of a new instance: immediately in immediate mode, otherwise queued.
     *
     * @param level the theme level
     * @param task  the placement task
     */
    static void start(ServerLevel level, PlacementTask task) {
        if (immediate) {
            task.runToCompletion(level);
        } else {
            QUEUE.add(task);
        }
    }

    /**
     * Checks whether an instance's structure is still being placed.
     *
     * @param id the instance id
     * @return {@code true} while placement is queued or running
     */
    public static boolean isPlacing(UUID id) {
        return QUEUE.stream().anyMatch(task -> task.instanceId().equals(id));
    }

    /** @return {@code true} while any placement is queued or running */
    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    /**
     * Runs a callback once an instance is ready: right away if it already is, otherwise when its placement
     * finishes. Nothing happens if the instance does not exist or is removed before it gets ready.
     *
     * @param level    the theme level
     * @param id       the instance id
     * @param callback the callback, called with the ready instance
     */
    public static void whenReady(ServerLevel level, UUID id, Consumer<ChallengeInstance> callback) {
        for (PlacementTask task : QUEUE) {
            if (task.instanceId().equals(id)) {
                task.whenReady(callback);
                return;
            }
        }
        InstanceManager.data(level).get(id).filter(ChallengeInstance::ready).ifPresent(callback);
    }

    /**
     * Advances the current placement by one tick's budget.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        PlacementTask task = QUEUE.peek();
        if (task == null) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(task.dimension());
        if (level == null) {
            QUEUE.poll();
            return;
        }
        task.step(level);
        if (task.isDone()) {
            QUEUE.poll();
        }
    }

    /**
     * Discards instances whose placement was interrupted by the last server stop. Runs after the slot clearing
     * of the last session has been resumed.
     *
     * @param event the server started event
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onServerStarted(ServerStartedEvent event) {
        discardUnfinished(event.getServer());
    }

    /**
     * Drops pending placements when the server stops; their instances are discarded on the next start.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    /**
     * Removes every instance that is not ready and not being placed, releasing its slot for clearing.
     *
     * @param server the server
     * @return the number of discarded instances
     */
    public static int discardUnfinished(MinecraftServer server) {
        int discarded = 0;
        for (ChallengeTheme theme : ChallengeThemes.all()) {
            ServerLevel level = server.getLevel(theme.dimension());
            if (level == null) {
                continue;
            }
            for (ChallengeInstance instance : List.copyOf(InstanceManager.data(level).all())) {
                if (!instance.ready() && !isPlacing(instance.id())) {
                    InstanceManager.close(level, instance.id());
                    ArchitectsTrials.LOGGER.info("Discarded instance {}: its placement was interrupted", instance.id());
                    discarded++;
                }
            }
        }
        return discarded;
    }

    /**
     * Drops the placement of one instance without finishing it, as a server stop does. The instance stays until
     * {@link #discardUnfinished} removes it. Meant for GameTests that simulate a restart.
     *
     * @param server the server
     * @param id     the instance id
     */
    public static void abandon(MinecraftServer server, UUID id) {
        QUEUE.removeIf(task -> {
            if (!task.instanceId().equals(id)) {
                return false;
            }
            ServerLevel level = server.getLevel(task.dimension());
            if (level != null) {
                task.cancel(level);
            }
            return true;
        });
    }
}

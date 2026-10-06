package com.gilfort.architectstrials.slot;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.InstancePlacements;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game event hooks that drive slot clearing.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SlotEvents {

    private SlotEvents() {
    }

    /**
     * Resumes slot clearing interrupted by the last server stop.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        SlotManager.resume(event.getServer());
    }

    /**
     * Advances slot clearing once per tick. Clearing pauses while instance structures are being placed, so both
     * never add up within one tick.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!InstancePlacements.busy()) {
            SlotManager.tick(event.getServer());
        }
    }

    /**
     * Drops pending in-memory clear tasks; they are persisted and resumed on the next start.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SlotManager.reset();
    }
}

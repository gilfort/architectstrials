package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for the chunk ticket types of Architect's Trials.
 */
public final class ModTicketTypes {

    /** Deferred register for ticket types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, ArchitectsTrials.MOD_ID);

    /**
     * Keeps the chunks of an area that is being cleared loaded, so their stored entities get loaded and can be
     * removed. Not persisted and without timeout; removed explicitly when clearing finishes.
     */
    public static final Supplier<TicketType> AREA_CLEAR = TICKET_TYPES.register("area_clear",
            () -> new TicketType(0L, TicketType.FLAG_LOADING));

    private ModTicketTypes() {
    }

    /**
     * Attaches the ticket type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        TICKET_TYPES.register(modEventBus);
    }
}

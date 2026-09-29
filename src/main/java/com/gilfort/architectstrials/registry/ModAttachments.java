package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.travel.EntryPoint;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registry holder for all data attachments of Architect's Trials.
 */
public final class ModAttachments {

    /** Deferred register for attachment types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArchitectsTrials.MOD_ID);

    /**
     * The point a player entered an Architect's Trials dimension from. Only present while the player is
     * inside; persisted across logout and server restart and kept on death.
     * <p>
     * Always access it via {@code getExistingData}/{@code setData}/{@code removeData}; there is no default value.
     */
    public static final Supplier<AttachmentType<EntryPoint>> ENTRY_POINT = ATTACHMENT_TYPES.register("entry_point",
            () -> AttachmentType.<EntryPoint>builder(() -> {
                        throw new IllegalStateException("The entry point attachment has no default value");
                    })
                    .serialize(EntryPoint.MAP_CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }

    /**
     * Attaches the attachment type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}

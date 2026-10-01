package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.rank.PlayerRank;
import com.gilfort.architectstrials.scroll.ParkedEffects;
import com.gilfort.architectstrials.run.RunStatistics;
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

    /**
     * Server tick until which a player is immune to all damage after the Dimension Ward has triggered.
     * Transient (not persisted); {@code 0} means no grace period.
     */
    public static final Supplier<AttachmentType<Long>> WARD_GRACE_UNTIL = ATTACHMENT_TYPES.register("ward_grace_until",
            () -> AttachmentType.builder(() -> 0L).build());

    /** Completed runs per theme and tier; persisted and kept on death. */
    public static final Supplier<AttachmentType<RunStatistics>> RUN_STATISTICS = ATTACHMENT_TYPES.register("run_statistics",
            () -> AttachmentType.builder(RunStatistics::new).serialize(RunStatistics.MAP_CODEC).copyOnDeath().build());

    /** Rank per theme; persisted, kept on death and synced to the owning player only. */
    public static final Supplier<AttachmentType<PlayerRank>> RANK = ATTACHMENT_TYPES.register("rank",
            () -> AttachmentType.builder(() -> new PlayerRank())
                    .serialize(PlayerRank.MAP_CODEC)
                    .sync((holder, player) -> holder == player, PlayerRank.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * Infinite scroll effects applied to a player and the player's own effects they replaced; persisted and kept
     * on death, removed once the player has left the instance.
     */
    public static final Supplier<AttachmentType<ParkedEffects>> PARKED_EFFECTS = ATTACHMENT_TYPES.register("parked_effects",
            () -> AttachmentType.builder(() -> ParkedEffects.EMPTY).serialize(ParkedEffects.MAP_CODEC).copyOnDeath().build());

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

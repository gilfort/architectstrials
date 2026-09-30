package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.portal.ChallengePortal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for all entity types of Architect's Trials.
 * <p>
 * Every entity type needs a matching {@code entity.architectstrials.<name>} entry in {@code en_us.json}.
 */
public final class ModEntityTypes {

    /** Deferred register for entity types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ArchitectsTrials.MOD_ID);

    /** The particle-only challenge portal opened by scrolls (1×2 blocks). */
    public static final Supplier<EntityType<ChallengePortal>> CHALLENGE_PORTAL = ENTITY_TYPES.register("challenge_portal",
            id -> EntityType.Builder.<ChallengePortal>of(ChallengePortal::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F)
                    .noLootTable()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntityTypes() {
    }

    /**
     * Attaches the entity type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}

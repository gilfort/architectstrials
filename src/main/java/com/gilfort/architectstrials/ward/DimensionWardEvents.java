package com.gilfort.architectstrials.ward;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Game event hooks of the {@link DimensionWard}.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class DimensionWardEvents {

    private DimensionWardEvents() {
    }

    /**
     * Cancels the death of protected players.
     * <p>
     * {@link LivingDeathEvent} fires only after vanilla totems have failed to save the entity. Running at
     * {@link EventPriority#LOWEST} and ignoring already canceled events makes the ward the very last resort,
     * after every other mod's death prevention.
     *
     * @param event the death event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DimensionWard.isProtected(player)) {
            event.setCanceled(true);
            DimensionWard.trigger(player);
        }
    }

    /**
     * Ignores all incoming damage during the grace period after the ward has triggered.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DimensionWard.isInGracePeriod(player)) {
            event.setCanceled(true);
        }
    }
}

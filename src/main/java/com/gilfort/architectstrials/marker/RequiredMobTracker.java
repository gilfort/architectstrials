package com.gilfort.architectstrials.marker;

import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.InstanceManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Required mobs (US-30): mobs of Direct Spawn Markers with the "Required" option.
 * <p>
 * A required mob glows for the remaining time of its instance (applied once, never re-applied) and carries its
 * instance id in its persistent data. Every final removal — death, {@code discard()} by commands or other mods,
 * conversion into another mob — counts as defeated; unloading with its chunk does not. The progress is stored in
 * the instance, so nothing is scanned or ticked.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class RequiredMobTracker {

    /** Key in a required mob's persistent data holding the id of its instance. */
    public static final String INSTANCE_KEY = ArchitectsTrials.MOD_ID + ":required_instance";

    private RequiredMobTracker() {
    }

    /**
     * Makes a freshly spawned mob a required mob of the instance being placed.
     *
     * @param context the placement context
     * @param mob     the spawned mob
     */
    static void markRequired(MarkerContext context, LivingEntity mob) {
        long remaining = context.instance().deadline() - ChallengeClock.now(context.level().getServer());
        int duration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, remaining));
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, true));
        mob.getPersistentData().putString(INSTANCE_KEY, context.instance().id().toString());
        context.addRequiredMob(mob.getUUID());
    }

    /**
     * Counts a required mob as defeated once it is removed for good.
     *
     * @param event the leave event
     */
    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) {
            return;
        }
        String instance = entity.getPersistentData().getStringOr(INSTANCE_KEY, "");
        if (instance.isEmpty()) {
            return;
        }
        try {
            InstanceManager.defeatRequiredMob(level, UUID.fromString(instance), entity.getUUID());
        } catch (IllegalArgumentException exception) {
            ArchitectsTrials.LOGGER.warn("Required mob {} has an invalid instance id {}", entity.getUUID(), instance);
        }
    }
}

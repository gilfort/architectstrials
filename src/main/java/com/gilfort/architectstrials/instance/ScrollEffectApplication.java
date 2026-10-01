package com.gilfort.architectstrials.instance;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.scroll.ScrollEffect;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Applies the effect upgrades of a scroll ({@link com.gilfort.architectstrials.scroll.ScrollEffects}) to an
 * instance:
 * <ul>
 * <li>{@code player} effects on every entry of a player; infinite ones are removed again when the player leaves
 * the instance (finite ones simply run out)</li>
 * <li>{@code mobs} effects on the first entry to all loaded mobs of the instance, and from then on to every mob
 * joining the instance area (spawned by spawners or loaded with its chunk)</li>
 * </ul>
 * Effects are added the vanilla way, so an existing stronger or longer effect is kept.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollEffectApplication {

    private ScrollEffectApplication() {
    }

    /**
     * Applies effects when a player enters an instance.
     *
     * @param player   the entering player
     * @param level    the theme level
     * @param instance the instance as it was before this entry
     */
    static void onEntry(ServerPlayer player, ServerLevel level, ChallengeInstance instance) {
        for (ScrollEffect effect : instance.effects().forTarget(ScrollEffect.Target.PLAYER)) {
            player.addEffect(effect.instance());
        }
        if (instance.roster().entrants().isEmpty() && !instance.effects().forTarget(ScrollEffect.Target.MOBS).isEmpty()) {
            AABB area = AABB.of(SlotManager.slot(level, instance.slot()).area(level.getMinY(), level.getMaxY()));
            level.getEntitiesOfClass(Mob.class, area, Mob::isAlive).forEach(mob -> applyMobEffects(mob, instance));
        }
    }

    /**
     * Removes infinite player effects when a player leaves an instance.
     *
     * @param player   the leaving player
     * @param instance the instance
     */
    static void onLeave(ServerPlayer player, ChallengeInstance instance) {
        for (ScrollEffect effect : instance.effects().forTarget(ScrollEffect.Target.PLAYER)) {
            MobEffectInstance active = player.getEffect(effect.effect());
            if (effect.duration() == MobEffectInstance.INFINITE_DURATION && active != null && active.isInfiniteDuration()) {
                player.removeEffect(effect.effect());
            }
        }
    }

    /**
     * Applies {@code mobs} effects to mobs joining an instance that has already been entered.
     *
     * @param event the join event
     */
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)
                || !ChallengeThemes.isChallengeDimension(level.dimension())) {
            return;
        }
        InstanceManager.findAt(level, mob.blockPosition())
                .filter(instance -> !instance.roster().entrants().isEmpty())
                .ifPresent(instance -> applyMobEffects(mob, instance));
    }

    private static void applyMobEffects(Mob mob, ChallengeInstance instance) {
        for (ScrollEffect effect : instance.effects().forTarget(ScrollEffect.Target.MOBS)) {
            mob.addEffect(effect.instance());
        }
    }
}

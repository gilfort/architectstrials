package com.gilfort.architectstrials.instance;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ParkedEffects;
import com.gilfort.architectstrials.scroll.ScrollEffect;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
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
 * <li>{@code player} effects on every entry of a player, always on top of an effect of the same type the player
 * already has (e.g. from a potion), which is parked and comes back afterwards:
 * <ul>
 * <li>finite scroll effect: the player's effect is parked behind it (vanilla hidden effect, extended by the scroll
 * effect's duration) and resumes in the challenge once the scroll effect has run out</li>
 * <li>infinite scroll effect: the player's effect is paused ({@link ParkedEffects}) and given back when the
 * player leaves; the scroll effect is removed then</li>
 * </ul></li>
 * <li>{@code mobs} effects on the first entry to all loaded mobs of the instance, and from then on to every mob
 * joining the instance area (spawned by spawners or loaded with its chunk)</li>
 * </ul>
 * Mob effects are added the vanilla way, so an existing stronger or longer effect is kept.
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
            applyToPlayer(player, effect);
        }
        if (instance.roster().entrants().isEmpty() && !instance.effects().forTarget(ScrollEffect.Target.MOBS).isEmpty()) {
            AABB area = AABB.of(SlotManager.slot(level, instance.slot()).area(level.getMinY(), level.getMaxY()));
            level.getEntitiesOfClass(Mob.class, area, Mob::isAlive).forEach(mob -> applyMobEffects(mob, instance));
        }
    }

    /**
     * Gives a player who left a challenge back what infinite scroll effects replaced: removes those scroll effects
     * and restores the parked effects with the duration they had on entry. Called by every way out of a challenge.
     *
     * @param player the player
     */
    public static void restore(ServerPlayer player) {
        ParkedEffects state = player.getData(ModAttachments.PARKED_EFFECTS);
        if (state.isEmpty()) {
            return;
        }
        for (Holder<MobEffect> type : state.applied()) {
            MobEffectInstance active = player.getEffect(type);
            if (active != null && active.isInfiniteDuration()) {
                player.removeEffect(type);
            }
        }
        state.parked().forEach(parked -> player.addEffect(new MobEffectInstance(parked)));
        player.removeData(ModAttachments.PARKED_EFFECTS);
    }

    private static void applyToPlayer(ServerPlayer player, ScrollEffect effect) {
        MobEffectInstance own = player.getEffect(effect.effect());
        if (effect.duration() == MobEffectInstance.INFINITE_DURATION) {
            player.setData(ModAttachments.PARKED_EFFECTS, player.getData(ModAttachments.PARKED_EFFECTS).with(effect.effect(), own));
            player.removeEffect(effect.effect());
            player.addEffect(effect.instance());
            return;
        }
        MobEffectInstance parked = own == null ? null : new MobEffectInstance(own.getEffect(),
                own.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION : own.getDuration() + effect.duration(),
                own.getAmplifier(), own.isAmbient(), own.isVisible(), own.showIcon());
        player.removeEffect(effect.effect());
        player.addEffect(effect.instance(parked));
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

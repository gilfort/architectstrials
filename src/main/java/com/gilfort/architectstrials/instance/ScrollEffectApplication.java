package com.gilfort.architectstrials.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.marker.RequiredMobTracker;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ParkedEffects;
import com.gilfort.architectstrials.scroll.ScrollEffect;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.structure.ChallengeAttribute;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Applies the effects of an instance to the players and mobs inside it, once per entry and without any tick:
 * <ul>
 * <li>player effects of the scroll ({@link com.gilfort.architectstrials.scroll.ScrollEffects}, target
 * {@code player}) and of the challenge ({@link ChallengeStructure#playerEffects()}, US-36) on every entry of a
 * player. "Permanent" effects ({@code duration -1}) last the remaining time of the instance. Effects of the same
 * type are layered as vanilla hidden effects: scroll effect on top, challenge effect behind it (it takes over once a
 * finite scroll effect runs out), the player's own effect behind both. Effects removed inside (milk) are not
 * re-applied. Leaving removes all of them and gives the player's own effects back with their duration from the
 * entry.</li>
 * <li>attribute modifiers of the challenge ({@link ChallengeStructure#playerAttributes()}) on entry, removed on
 * leaving</li>
 * <li>mob effects of the challenge ({@link ChallengeStructure#mobEffects()}, US-41) and {@code mobs} effects of the
 * scroll on the first entry to all loaded mobs of the instance, and from then on to every mob joining the instance
 * area (spawned by spawners or loaded with its chunk); {@code duration -1} = infinite</li>
 * </ul>
 * Mob effects are added the vanilla way, so an existing stronger or longer effect is kept. Their types are listed in
 * the mob's persistent data ({@link #APPLIED_EFFECTS_KEY}): a mob that leaves its challenge (e.g. released from a
 * capture tool) loses them and the required-mob glow as soon as it joins a level outside challenge dimensions
 * (US-44).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollEffectApplication {

    /** Key in a mob's persistent data listing the ids of the effects its instance applied (US-44). */
    public static final String APPLIED_EFFECTS_KEY = ArchitectsTrials.MOD_ID + ":applied_effects";

    private ScrollEffectApplication() {
    }

    /**
     * Applies effects and attribute modifiers when a player enters an instance.
     *
     * @param player   the entering player
     * @param level    the theme level
     * @param instance the instance as it was before this entry
     */
    static void onEntry(ServerPlayer player, ServerLevel level, ChallengeInstance instance) {
        int remaining = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, instance.remainingTicks(ChallengeClock.now(level.getServer()))));
        Optional<ChallengeStructure> structure = ChallengeStructures.get(instance.structure());
        Map<Holder<MobEffect>, List<Layer>> layers = new LinkedHashMap<>();
        for (ScrollEffect effect : instance.effects().forTarget(ScrollEffect.Target.PLAYER)) {
            layers.computeIfAbsent(effect.effect(), type -> new ArrayList<>()).add(new Layer(effect.amplifier(), duration(effect.duration(), remaining)));
        }
        structure.ifPresent(found -> found.playerEffects().forEach(effect -> layers.computeIfAbsent(effect.effect(), type -> new ArrayList<>())
                .add(new Layer(effect.amplifier(), duration(effect.duration(), remaining)))));
        layers.forEach((type, typeLayers) -> applyLayers(player, type, typeLayers));
        structure.ifPresent(found -> applyAttributes(player, found.playerAttributes()));

        boolean mobEffects = !instance.effects().forTarget(ScrollEffect.Target.MOBS).isEmpty()
                || structure.map(found -> !found.mobEffects().isEmpty()).orElse(false);
        if (instance.roster().entrants().isEmpty() && mobEffects) {
            AABB area = AABB.of(SlotManager.slot(level, instance.slot()).area(level.getMinY(), level.getMaxY()));
            level.getEntitiesOfClass(Mob.class, area, Mob::isAlive).forEach(mob -> applyMobEffects(mob, instance));
        }
    }

    /**
     * Gives a player who left a challenge back what entering replaced: removes the applied effects and attribute
     * modifiers and restores the parked effects with the duration they had on entry. Called by every way out of a
     * challenge.
     *
     * @param player the player
     */
    public static void restore(ServerPlayer player) {
        ParkedEffects state = player.getData(ModAttachments.PARKED_EFFECTS);
        if (state.isEmpty()) {
            return;
        }
        state.applied().forEach(player::removeEffect);
        state.parked().forEach(parked -> player.addEffect(new MobEffectInstance(parked)));
        for (ParkedEffects.Modifier modifier : state.modifiers()) {
            AttributeInstance attribute = player.getAttribute(modifier.attribute());
            if (attribute != null) {
                attribute.removeModifier(modifier.id());
            }
        }
        player.removeData(ModAttachments.PARKED_EFFECTS);
    }

    private static int duration(int configured, int remaining) {
        return configured == MobEffectInstance.INFINITE_DURATION ? remaining : configured;
    }

    /**
     * Replaces the player's effect of a type by the layers (top first), with the player's own effect parked at the
     * bottom. Hidden effects tick down together with the top one, so every lower layer is extended by the longest
     * duration above it.
     */
    private static void applyLayers(ServerPlayer player, Holder<MobEffect> type, List<Layer> layers) {
        MobEffectInstance own = player.getEffect(type);
        player.setData(ModAttachments.PARKED_EFFECTS, player.getData(ModAttachments.PARKED_EFFECTS).with(type, own));
        int above = layers.stream().mapToInt(Layer::duration).max().orElse(0);
        MobEffectInstance chain = own == null ? null : new MobEffectInstance(type,
                own.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION : own.getDuration() + above,
                own.getAmplifier(), own.isAmbient(), own.isVisible(), own.showIcon());
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            chain = new MobEffectInstance(type, layer.duration(), layer.amplifier(), false, false, true, chain);
        }
        player.removeEffect(type);
        player.addEffect(chain);
    }

    private static void applyAttributes(ServerPlayer player, List<ChallengeAttribute> attributes) {
        for (int i = 0; i < attributes.size(); i++) {
            ChallengeAttribute entry = attributes.get(i);
            AttributeInstance attribute = player.getAttribute(entry.attribute());
            if (attribute == null) {
                ArchitectsTrials.LOGGER.warn("Players have no attribute {}; challenge attribute skipped", entry.attribute().getRegisteredName());
                continue;
            }
            Identifier id = ArchitectsTrials.id("challenge_attribute_" + i);
            attribute.removeModifier(id);
            attribute.addPermanentModifier(new AttributeModifier(id, entry.amount(), entry.operation()));
            player.setData(ModAttachments.PARKED_EFFECTS, player.getData(ModAttachments.PARKED_EFFECTS)
                    .withModifier(new ParkedEffects.Modifier(entry.attribute(), id)));
        }
    }

    /** One layer of an effect type: amplifier and duration in ticks. */
    private record Layer(int amplifier, int duration) {
    }

    /**
     * Applies {@code mobs} effects to mobs joining an instance that has already been entered.
     *
     * @param event the join event
     */
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ChallengeThemes.isChallengeDimension(level.dimension())) {
            defuse(living);
            return;
        }
        if (!(living instanceof Mob mob)) {
            return;
        }
        InstanceManager.findAt(level, mob.blockPosition())
                .filter(instance -> !instance.roster().entrants().isEmpty())
                .ifPresent(instance -> applyMobEffects(mob, instance));
    }

    private static void applyMobEffects(Mob mob, ChallengeInstance instance) {
        ChallengeStructures.get(instance.structure()).ifPresent(structure -> structure.mobEffects().forEach(effect -> {
            mob.addEffect(new MobEffectInstance(effect.effect(), effect.duration(), effect.amplifier()));
            markApplied(mob, effect.effect());
        }));
        for (ScrollEffect effect : instance.effects().forTarget(ScrollEffect.Target.MOBS)) {
            mob.addEffect(effect.instance());
            markApplied(mob, effect.effect());
        }
    }

    /** Lists an effect type in the mob's applied effects. */
    private static void markApplied(Mob mob, Holder<MobEffect> effect) {
        CompoundTag data = mob.getPersistentData();
        ListTag applied = data.getListOrEmpty(APPLIED_EFFECTS_KEY);
        StringTag id = StringTag.valueOf(effect.getRegisteredName());
        if (!applied.contains(id)) {
            applied.add(id);
        }
        data.put(APPLIED_EFFECTS_KEY, applied);
    }

    /**
     * Removes what a challenge gave a mob that is now outside challenge dimensions: the applied effects and the
     * required-mob glow. Equipment stays (its drop chance stays 0 %).
     *
     * @param entity the entity joining a level outside challenge dimensions
     */
    public static void defuse(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(APPLIED_EFFECTS_KEY)) {
            for (Tag id : data.getListOrEmpty(APPLIED_EFFECTS_KEY)) {
                id.asString().map(Identifier::tryParse).flatMap(BuiltInRegistries.MOB_EFFECT::get).ifPresent(entity::removeEffect);
            }
            data.remove(APPLIED_EFFECTS_KEY);
        }
        if (data.contains(RequiredMobTracker.INSTANCE_KEY)) {
            entity.removeEffect(MobEffects.GLOWING);
            data.remove(RequiredMobTracker.INSTANCE_KEY);
        }
    }
}

package com.gilfort.architectstrials.run;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Advancement criterion {@code architectstrials:run_completed}, triggered whenever a player completes a run.
 * <p>
 * All conditions are optional:
 * <pre>{@code
 * "conditions": {
 *   "theme": "mypack:nether",        // only runs of this theme (default: any theme)
 *   "tier": { "min": 2, "max": 3 },  // only runs of these tiers (default: any tier)
 *   "runs": { "min": 5 }             // number of completed runs matching theme and tier (default: any)
 * }
 * }</pre>
 * The run count comes from the player's {@link RunStatistics} and already includes the run just completed.
 */
public class RunCompletedTrigger extends SimpleCriterionTrigger<RunCompletedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /**
     * Triggers the criterion for a completed run.
     *
     * @param player     the player
     * @param theme      the theme of the completed run
     * @param tier       the tier of the completed run
     * @param statistics the player's run statistics, including this run
     */
    public void trigger(ServerPlayer player, Identifier theme, int tier, RunStatistics statistics) {
        this.trigger(player, instance -> instance.matches(theme, tier, statistics));
    }

    /**
     * Conditions of the criterion.
     *
     * @param player the optional player predicate
     * @param theme  the optional theme
     * @param tier   the tier range
     * @param runs   the required number of completed runs matching theme and tier
     */
    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<Identifier> theme,
            MinMaxBounds.Ints tier, MinMaxBounds.Ints runs) implements SimpleCriterionTrigger.SimpleInstance {

        /** Codec of the criterion conditions. */
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Identifier.CODEC.optionalFieldOf("theme").forGetter(TriggerInstance::theme),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("tier", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::tier),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("runs", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::runs)
        ).apply(instance, TriggerInstance::new));

        /**
         * Checks the conditions against a completed run.
         *
         * @param completedTheme the theme of the completed run
         * @param completedTier  the tier of the completed run
         * @param statistics     the player's run statistics
         * @return {@code true} if all conditions hold
         */
        public boolean matches(Identifier completedTheme, int completedTier, RunStatistics statistics) {
            if (this.theme.isPresent() && !this.theme.get().equals(completedTheme)) {
                return false;
            }
            if (!this.tier.matches(completedTier)) {
                return false;
            }
            int minTier = this.tier.min().orElse(1);
            int maxTier = this.tier.max().orElse(Integer.MAX_VALUE);
            return this.runs.matches(statistics.completed(this.theme.orElse(null), minTier, maxTier));
        }
    }
}

package com.gilfort.architectstrials.run;

import java.util.Optional;

import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.block.ExitGroup;
import com.gilfort.architectstrials.block.LootTableReference;
import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupHolder;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModCriteriaTriggers;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Completes the runs of players walking through open challenge exits.
 * <p>
 * Detection is event-based: the invisible {@link ChallengeExitPortalBlock}s in the portal space call
 * {@link #completeThrough} when vanilla reports a player touching them, so nothing is checked while nobody uses an
 * exit. Each player completes individually; the instance keeps running for everyone else.
 */
public final class RunCompletion {

    private RunCompletion() {
    }

    /**
     * Completes the run of a player who walked through an open exit group of their instance, granting the bonus
     * set on the group's bases.
     *
     * @param player   the player
     * @param level    the challenge level
     * @param instance the instance the exit belongs to
     * @param group    the used exit group
     */
    public static void completeThrough(ServerPlayer player, ServerLevel level, ChallengeInstance instance, ExitGroup group) {
        complete(player, instance, bonusOverride(level, group), bonusSetup(level, group));
    }

    /**
     * Returns the bonus loot table set on any base of an exit group.
     *
     * @param level the level
     * @param group the exit group
     * @return the first loot table reference found, if any
     */
    static Optional<ResourceKey<LootTable>> bonusOverride(ServerLevel level, ExitGroup group) {
        for (BlockPos base : group.members()) {
            if (level.getBlockEntity(base) instanceof LootTableReference reference && reference.lootTableReference().isPresent()) {
                return reference.lootTableReference();
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the bonus loot setup set on any base of an exit group.
     *
     * @param level the level
     * @param group the exit group
     * @return the first non-empty setup found, or {@link LootSetup#EMPTY}
     */
    static LootSetup bonusSetup(ServerLevel level, ExitGroup group) {
        for (BlockPos base : group.members()) {
            if (level.getBlockEntity(base) instanceof LootSetupHolder holder && !holder.lootSetup(false).isEmpty()) {
                return holder.lootSetup(false);
            }
        }
        return LootSetup.EMPTY;
    }

    /**
     * Completes a run like {@link #complete(ServerPlayer, ChallengeInstance, Optional, LootSetup)} without a loot
     * setup on the exit.
     *
     * @param player        the player
     * @param instance      the completed instance
     * @param bonusOverride the bonus loot table set on the used exit, if any
     */
    public static void complete(ServerPlayer player, ChallengeInstance instance, Optional<ResourceKey<LootTable>> bonusOverride) {
        complete(player, instance, bonusOverride, LootSetup.EMPTY);
    }

    /**
     * Completes a run: counts it, triggers the advancement criterion, returns the player to their entry point,
     * grants the completion bonus and fires {@link RunCompletedEvent}.
     *
     * @param player        the player
     * @param instance      the completed instance
     * @param bonusOverride the bonus loot table set on the used exit, if any
     * @param bonusSetup    the bonus loot setup set on the used exit (takes precedence if not empty)
     */
    public static void complete(ServerPlayer player, ChallengeInstance instance, Optional<ResourceKey<LootTable>> bonusOverride,
            LootSetup bonusSetup) {
        RunStatistics statistics = player.getData(ModAttachments.RUN_STATISTICS);
        statistics.increment(instance.theme(), instance.tier());
        InstanceManager.markCompleted(player.level(), instance.id(), player.getUUID());
        ChallengeTravel.returnToEntryPoint(player);
        if (bonusSetup.isEmpty()) {
            CompletionBonus.grant(player, instance, bonusOverride);
        } else {
            CompletionBonus.grant(player, bonusSetup);
        }
        ModCriteriaTriggers.RUN_COMPLETED.get().trigger(player, instance.theme(), instance.tier(), statistics);
        Component theme = ChallengeThemes.get(instance.theme()).map(ChallengeTheme::displayName)
                .orElse(Component.literal(instance.theme().toString()));
        player.sendSystemMessage(Component.translatable("message.architectstrials.run_completed", theme, instance.tier()));
        NeoForge.EVENT_BUS.post(new RunCompletedEvent(player, instance));
    }
}

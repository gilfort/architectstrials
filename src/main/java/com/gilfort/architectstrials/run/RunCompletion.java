package com.gilfort.architectstrials.run;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ExitGroup;
import com.gilfort.architectstrials.block.LootTableReference;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Detects players walking through open challenge exits and completes their run.
 * <p>
 * Each player completes individually; the instance keeps running for everyone else.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class RunCompletion {

    /** Exits are checked every this many ticks. */
    private static final int CHECK_INTERVAL_TICKS = 2;

    private RunCompletion() {
    }

    /**
     * Checks all challenge dimensions for players inside open exits.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (ChallengeTheme theme : ChallengeThemes.all()) {
            ServerLevel level = server.getLevel(theme.dimension());
            if (level != null && !level.players().isEmpty()) {
                checkExits(level);
            }
        }
    }

    /**
     * Completes the run of every player in a level who stands in an open exit of their instance.
     *
     * @param level the challenge level
     */
    public static void checkExits(ServerLevel level) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.isAlive() || player.isSpectator() || !player.hasData(ModAttachments.ENTRY_POINT)) {
                continue;
            }
            Optional<ChallengeInstance> instance = InstanceManager.findAt(level, player.blockPosition());
            if (instance.isEmpty()) {
                continue;
            }
            for (BlockPos exit : instance.get().exits()) {
                Optional<ExitGroup> group = ExitGroup.find(level, exit);
                if (group.isPresent() && !group.get().locked() && player.getBoundingBox().intersects(group.get().portalArea())) {
                    complete(player, instance.get(), bonusOverride(level, group.get()));
                    break;
                }
            }
        }
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
     * Completes a run: counts it, triggers the advancement criterion, returns the player to their entry point,
     * grants the completion bonus and fires {@link RunCompletedEvent}.
     *
     * @param player        the player
     * @param instance      the completed instance
     * @param bonusOverride the bonus loot table set on the used exit, if any
     */
    public static void complete(ServerPlayer player, ChallengeInstance instance, Optional<ResourceKey<LootTable>> bonusOverride) {
        RunStatistics statistics = player.getData(ModAttachments.RUN_STATISTICS);
        statistics.increment(instance.theme(), instance.tier());
        ChallengeTravel.returnToEntryPoint(player);
        CompletionBonus.grant(player, instance, bonusOverride);
        ModCriteriaTriggers.RUN_COMPLETED.get().trigger(player, instance.theme(), instance.tier(), statistics);
        Component theme = ChallengeThemes.get(instance.theme()).map(ChallengeTheme::displayName)
                .orElse(Component.literal(instance.theme().toString()));
        player.sendSystemMessage(Component.translatable("message.architectstrials.run_completed", theme, instance.tier()));
        NeoForge.EVENT_BUS.post(new RunCompletedEvent(player, instance));
    }
}

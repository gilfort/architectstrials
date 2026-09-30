package com.gilfort.architectstrials.instance;

import java.util.List;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the lifecycle of all challenge instances: advances the {@link ChallengeClock}, warns participants
 * before the time limit, ejects them when it expires, and cleans up instances nobody belongs to anymore.
 * <p>
 * Cleanup rule (portal timer takes precedence): an instance is removed only when its portal is closed <em>and</em>
 * it has no participants, online or offline. Portals whose chunk is unloaded cannot close themselves, so an
 * open portal counts as closed a short grace period after its deadline.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class InstanceLifecycle {

    /** Lifecycle checks run every this many ticks (one second). */
    private static final int UPDATE_INTERVAL_TICKS = ChallengeClock.TICKS_PER_SECOND;

    /** Grace period after a portal deadline before an unloaded portal counts as closed. */
    private static final long PORTAL_GRACE_TICKS = 5L * ChallengeClock.TICKS_PER_SECOND;

    private static final long FIVE_MINUTES = 5L * 60L * ChallengeClock.TICKS_PER_SECOND;
    private static final long ONE_MINUTE = 60L * ChallengeClock.TICKS_PER_SECOND;
    private static final long COUNTDOWN = 10L * ChallengeClock.TICKS_PER_SECOND;

    private InstanceLifecycle() {
    }

    /**
     * Advances the clock every tick and updates all instances once per second.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ChallengeClock.of(server).advance(1L);
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS == 0) {
            update(server);
        }
    }

    /**
     * Updates every instance of every theme: warnings, time limit, portal grace and cleanup.
     *
     * @param server the server
     */
    public static void update(MinecraftServer server) {
        long now = ChallengeClock.now(server);
        for (ChallengeTheme theme : ChallengeThemes.all()) {
            ServerLevel level = server.getLevel(theme.dimension());
            if (level == null) {
                continue;
            }
            for (ChallengeInstance instance : List.copyOf(InstanceManager.data(level).all())) {
                update(level, instance, now);
            }
        }
    }

    private static void update(ServerLevel level, ChallengeInstance instance, long now) {
        long remaining = instance.deadline() - now;
        if (remaining <= 0) {
            expire(level, instance);
            return;
        }
        warn(level, instance, remaining);

        ChallengeInstance current = instance;
        if (current.portalOpen() && now > current.portalDeadline() + PORTAL_GRACE_TICKS) {
            current = current.withPortalDeadline(-1L);
            InstanceManager.update(level, current);
        }
        if (current.state() == ChallengeInstance.LifecycleState.CLEANUP_DUE) {
            InstanceManager.close(level, current.id());
            ArchitectsTrials.LOGGER.debug("Cleaned up instance {} (portal closed, no participants)", current.id());
        }
    }

    /**
     * Ends an instance whose time limit expired: returns all online participants (not a completed run) and
     * removes the instance. Offline participants are returned when they log in.
     */
    private static void expire(ServerLevel level, ChallengeInstance instance) {
        for (ServerPlayer player : onlineParticipants(level, instance)) {
            ChallengeTravel.returnToEntryPoint(player);
            player.sendSystemMessage(Component.translatable("message.architectstrials.time.expired"));
        }
        InstanceManager.close(level, instance.id());
        ArchitectsTrials.LOGGER.debug("Instance {} expired", instance.id());
    }

    /**
     * Sends chat warnings when 5 and 1 minutes remain and an action-bar countdown during the last 10 seconds.
     */
    private static void warn(ServerLevel level, ChallengeInstance instance, long remaining) {
        long previous = remaining + UPDATE_INTERVAL_TICKS;
        Component message = null;
        boolean overlay = false;
        if (previous > FIVE_MINUTES && remaining <= FIVE_MINUTES) {
            message = Component.translatable("message.architectstrials.time.remaining_minutes", 5);
        } else if (previous > ONE_MINUTE && remaining <= ONE_MINUTE) {
            message = Component.translatable("message.architectstrials.time.remaining_minutes", 1);
        } else if (remaining <= COUNTDOWN) {
            long seconds = (remaining + ChallengeClock.TICKS_PER_SECOND - 1) / ChallengeClock.TICKS_PER_SECOND;
            message = Component.translatable("message.architectstrials.time.countdown", seconds);
            overlay = true;
        }
        if (message == null) {
            return;
        }
        for (ServerPlayer player : onlineParticipants(level, instance)) {
            if (overlay) {
                player.sendOverlayMessage(message);
            } else {
                player.sendSystemMessage(message);
            }
        }
    }

    private static List<ServerPlayer> onlineParticipants(ServerLevel level, ChallengeInstance instance) {
        return instance.participants().stream()
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(player -> player != null && player.level() == level)
                .toList();
    }
}

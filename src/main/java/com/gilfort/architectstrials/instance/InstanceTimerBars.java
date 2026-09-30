package com.gilfort.architectstrials.instance;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Shows the remaining time of an instance to its participants as a boss bar ("Time left: mm:ss"). The bar
 * drains with the time limit and turns red during the last minute. Bars live in memory only and are rebuilt by
 * the lifecycle tick, so they reappear after a restart.
 */
public final class InstanceTimerBars {

    private static final long LAST_MINUTE_TICKS = 60L * ChallengeClock.TICKS_PER_SECOND;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private InstanceTimerBars() {
    }

    /**
     * Updates the bar of an instance and shows it exactly to the given players.
     *
     * @param instance  the instance
     * @param remaining the remaining time in ticks
     * @param viewers   the online participants inside the instance
     */
    static void update(ChallengeInstance instance, long remaining, List<ServerPlayer> viewers) {
        ServerBossEvent bar = BARS.computeIfAbsent(instance.id(), id -> new ServerBossEvent(UUID.randomUUID(),
                Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
        long seconds = Math.max(0L, remaining) / ChallengeClock.TICKS_PER_SECOND;
        bar.setName(Component.translatable("bossbar.architectstrials.time_left",
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
        bar.setProgress(instance.timeLimit() > 0 ? Math.clamp((float) remaining / instance.timeLimit(), 0.0F, 1.0F) : 1.0F);
        bar.setColor(remaining <= LAST_MINUTE_TICKS ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!viewers.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer viewer : viewers) {
            bar.addPlayer(viewer);
        }
    }

    /**
     * Hides the bar of an instance from one player immediately (e.g. after they left the instance).
     *
     * @param instanceId the instance id
     * @param player     the player
     */
    static void hideFrom(UUID instanceId, ServerPlayer player) {
        ServerBossEvent bar = BARS.get(instanceId);
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    /**
     * Hides and forgets the bar of an instance.
     *
     * @param instanceId the instance id
     */
    static void remove(UUID instanceId) {
        ServerBossEvent bar = BARS.remove(instanceId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /**
     * Hides and forgets all bars; called when the server stops.
     */
    static void clear() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /**
     * Returns the bar of an instance, if one is shown.
     *
     * @param instanceId the instance id
     * @return the bar, or {@code null}
     */
    public static ServerBossEvent get(UUID instanceId) {
        return BARS.get(instanceId);
    }
}

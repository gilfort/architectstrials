package com.gilfort.architectstrials.run;

import com.gilfort.architectstrials.instance.ChallengeInstance;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@code NeoForge.EVENT_BUS} when a player completes a run by walking through an open exit. This is
 * the only way a run counts as completed — deaths, time limits and logouts never fire it.
 * <p>
 * Fired after the player has been returned to their entry point and the statistic has been counted.
 */
public class RunCompletedEvent extends Event {

    private final ServerPlayer player;
    private final ChallengeInstance instance;

    /**
     * Creates the event.
     *
     * @param player   the player who completed the run
     * @param instance the completed instance
     */
    public RunCompletedEvent(ServerPlayer player, ChallengeInstance instance) {
        this.player = player;
        this.instance = instance;
    }

    /** @return the player who completed the run */
    public ServerPlayer getPlayer() {
        return this.player;
    }

    /** @return the completed instance (theme, tier, structure) */
    public ChallengeInstance getInstance() {
        return this.instance;
    }
}

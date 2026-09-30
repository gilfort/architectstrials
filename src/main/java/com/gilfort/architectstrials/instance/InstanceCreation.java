package com.gilfort.architectstrials.instance;

import net.minecraft.network.chat.Component;

/**
 * Outcome of an attempt to create a challenge instance.
 */
public sealed interface InstanceCreation {

    /**
     * The instance was created and its structure placed.
     *
     * @param instance the new instance
     */
    record Success(ChallengeInstance instance) implements InstanceCreation {
    }

    /**
     * The instance could not be created; nothing was consumed or left occupied.
     *
     * @param reason a player-facing explanation
     */
    record Failure(Component reason) implements InstanceCreation {
    }
}

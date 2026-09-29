package com.gilfort.architectstrials.travel;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Game event hooks for player travel into and out of Architect's Trials dimensions.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeTravelEvents {

    private ChallengeTravelEvents() {
    }

    /**
     * Returns players who log in inside an Architect's Trials dimension without a running challenge.
     * <p>
     * Challenge instances do not exist yet, so every such login is treated as "instance gone". The
     * instance lifecycle story extends this check so that players whose instance still runs stay inside.
     *
     * @param event the login event
     */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ChallengeTravel.isModDimension(player.level().dimension())
                && !hasRunningChallenge(player)) {
            ChallengeTravel.exit(player);
            player.sendSystemMessage(Component.translatable("message.architectstrials.returned_on_login"));
        }
    }

    /**
     * Checks whether the player still belongs to a running challenge instance.
     *
     * @param player the player
     * @return always {@code false} until challenge instances are implemented
     */
    private static boolean hasRunningChallenge(ServerPlayer player) {
        return false;
    }
}

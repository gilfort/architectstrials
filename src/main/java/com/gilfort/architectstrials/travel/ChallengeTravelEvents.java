package com.gilfort.architectstrials.travel;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.InstanceManager;

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
     * Returns players who log in inside an Architect's Trials dimension unless they still participate in a
     * running instance (offline participants keep their instance alive until its time limit).
     *
     * @param event the login event
     */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ChallengeTravel.isModDimension(player.level().dimension())
                && !InstanceManager.isParticipant(player)) {
            ChallengeTravel.exit(player);
            player.sendSystemMessage(Component.translatable("message.architectstrials.returned_on_login"));
        }
    }
}

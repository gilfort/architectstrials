package com.gilfort.architectstrials.travel;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.theme.ChallengeThemes;

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
     * Returns players who log in inside a challenge dimension unless they still participate in a running
     * instance (offline participants keep their instance alive until its time limit). Builders logging in
     * inside the editor stay there. Players who logged out in a dimension that no longer exists are returned to
     * their stored entry point.
     *
     * @param event the login event
     */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ChallengeTravel.isModDimension(player.level().dimension())) {
            boolean themeRemoved = player.getExistingData(ModAttachments.ENTRY_POINT).flatMap(EntryPoint::instance)
                    .map(ref -> player.level().getServer().getLevel(ref.dimension()) == null)
                    .orElse(false);
            if (themeRemoved) {
                // Logged out inside a theme dimension that no longer exists (theme or mod removed): vanilla put them
                // into the overworld, but their entry point is still stored.
                ChallengeTravel.returnToEntryPoint(player);
                player.sendSystemMessage(Component.translatable("message.architectstrials.returned_missing_dimension"));
            }
            return;
        }
        if (!ChallengeThemes.isChallengeDimension(player.level().dimension())) {
            return;
        }
        if (!InstanceManager.isParticipant(player)) {
            ChallengeTravel.exit(player);
            player.sendSystemMessage(Component.translatable("message.architectstrials.returned_on_login"));
            return;
        }
        player.getExistingData(ModAttachments.ENTRY_POINT).flatMap(EntryPoint::instance)
                .flatMap(ref -> InstanceManager.data(player.level()).get(ref.id()))
                .ifPresent(instance -> InstanceManager.ensureExitPortals(player.level(), instance));
    }
}

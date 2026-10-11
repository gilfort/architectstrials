package com.gilfort.architectstrials.instance;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModTags;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Usage block for forbidden items and blocks inside challenges (US-44). In challenge theme dimensions (not the
 * editor), using an item of {@link ModTags#FORBIDDEN_ITEMS} in the acting hand is cancelled, and blocks of
 * {@link ModTags#FORBIDDEN_BLOCKS} cannot be placed. Clicked blocks still react (chests and doors open). Only active
 * uses are covered: passive inventory effects, key-bind abilities and uses without a player are not.
 * <p>
 * Runs on the server only; the client does not know which dimensions are challenges.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ForbiddenUse {

    private ForbiddenUse() {
    }

    /**
     * Cancels right clicks into the air with a forbidden item (throwables, warp scrolls, food, bows).
     *
     * @param event the right click event
     */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isForbidden(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            deny(event.getEntity());
        }
    }

    /**
     * Denies the item part of a right click on a block with a forbidden item; the block itself still reacts.
     *
     * @param event the right click event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isForbidden(event.getEntity(), event.getItemStack())) {
            event.setUseItem(TriState.FALSE);
            deny(event.getEntity());
        }
    }

    /**
     * Cancels right clicks on entities with a forbidden item (capture tools, leads, name tags).
     *
     * @param event the interaction event
     */
    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isForbidden(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            deny(event.getEntity());
        }
    }

    /**
     * Cancels attacks with a forbidden item in the main hand (tools that capture on hit).
     *
     * @param event the attack event
     */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (isForbidden(event.getEntity(), event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    /**
     * Cancels left clicks on blocks with a forbidden item; the message is shown once per click, not while held.
     *
     * @param event the left click event
     */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isForbidden(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                deny(event.getEntity());
            }
        }
    }

    /**
     * Cancels placing a forbidden block in a challenge, by players and by anything else that fires the event.
     *
     * @param event the place event
     */
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()
                && ChallengeThemes.isChallengeDimension(level.dimension()) && event.getPlacedBlock().is(ModTags.FORBIDDEN_BLOCKS)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                deny(player);
            }
        }
    }

    /**
     * Checks whether a player uses a forbidden item inside a challenge.
     *
     * @param player the acting player
     * @param stack  the used stack
     * @return {@code true} if the use must be cancelled
     */
    public static boolean isForbidden(Player player, ItemStack stack) {
        return player instanceof ServerPlayer && !stack.isEmpty() && stack.is(ModTags.FORBIDDEN_ITEMS)
                && ChallengeThemes.isChallengeDimension(player.level().dimension());
    }

    private static void deny(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.architectstrials.forbidden_use"));
        }
    }
}

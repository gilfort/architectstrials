package com.gilfort.architectstrials.ward;

import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodConstants;

/**
 * The Dimension Ward: lossless death protection inside challenge dimensions.
 * <p>
 * The ward is a <em>state</em>, not a removable effect: a player is protected whenever they are inside a
 * challenge dimension and have a stored entry point. It cannot be cleared by milk, commands or other mods.
 * It acts only after every other death-prevention mechanism (real totems, other mods) has failed and then
 * returns the player to their entry point with full health and hunger and without any effects.
 */
public final class DimensionWard {

    /** Entity event id of the vanilla totem-of-undying animation (particles, sound, item overlay). */
    private static final byte TOTEM_ANIMATION_EVENT = 35;

    /** Duration of full damage immunity after the ward has triggered, in ticks. */
    public static final int GRACE_TICKS = 40;

    private DimensionWard() {
    }

    /**
     * Checks whether a player is currently protected by the ward.
     *
     * @param player the player
     * @return {@code true} if the player is inside a challenge dimension with a stored entry point
     */
    public static boolean isProtected(ServerPlayer player) {
        return ChallengeThemes.isChallengeDimension(player.level().dimension())
                && player.hasData(ModAttachments.ENTRY_POINT);
    }

    /**
     * Saves a player from death: clears all effects and fire, restores full health and hunger, plays the
     * totem animation, grants a short grace period and returns the player to their entry point.
     * <p>
     * The caller is responsible for canceling the death itself.
     *
     * @param player the player to save
     */
    public static void trigger(ServerPlayer player) {
        player.stopRiding();
        player.removeAllEffects();
        player.clearFire();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(FoodConstants.MAX_FOOD);
        player.getFoodData().setSaturation(FoodConstants.MAX_SATURATION);
        player.level().broadcastEntityEvent(player, TOTEM_ANIMATION_EVENT);
        player.setData(ModAttachments.WARD_GRACE_UNTIL, (long) player.level().getServer().getTickCount() + GRACE_TICKS);
        ChallengeTravel.returnToEntryPoint(player);
    }

    /**
     * Checks whether a player is inside the post-trigger grace period.
     *
     * @param player the player
     * @return {@code true} if all incoming damage must be ignored
     */
    public static boolean isInGracePeriod(ServerPlayer player) {
        return player.getData(ModAttachments.WARD_GRACE_UNTIL) > player.level().getServer().getTickCount();
    }
}

package com.gilfort.architectstrials.scroll;

import java.util.Optional;

import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Opens a challenge portal from a scroll.
 * <p>
 * Order: validate (incl. the player's difficulty level for the theme) → create the instance → spawn the portal →
 * consume the scroll. The scroll is consumed only
 * if everything succeeded; every failure leaves it untouched and reports a lang-defined reason.
 */
public final class ScrollActivation {

    private ScrollActivation() {
    }

    /**
     * Outcome of a scroll activation.
     *
     * @param portal  the opened portal, empty on failure
     * @param message the failure reason, empty on success
     */
    public record Result(Optional<ChallengePortal> portal, Optional<Component> message) {

        static Result success(ChallengePortal portal) {
            return new Result(Optional.of(portal), Optional.empty());
        }

        static Result failure(Component message) {
            return new Result(Optional.empty(), Optional.of(message));
        }

        /** @return {@code true} if a portal was opened */
        public boolean succeeded() {
            return this.portal.isPresent();
        }
    }

    /**
     * Tries to open a portal at {@code portalPos} (the lower of the two blocks the portal occupies).
     *
     * @param player    the player using the scroll
     * @param stack     the scroll stack; shrunk by one on success
     * @param portalPos the lower block position of the portal
     * @param portalYaw the yaw the portal faces
     * @return the outcome
     */
    public static Result activate(ServerPlayer player, ItemStack stack, BlockPos portalPos, float portalYaw) {
        ScrollTarget target = stack.get(ModDataComponents.SCROLL_TARGET.get());
        if (target == null) {
            return Result.failure(Component.translatable("message.architectstrials.scroll.blank"));
        }
        ServerLevel level = player.level();
        if (ChallengeTravel.isModDimension(level.dimension())) {
            return Result.failure(Component.translatable("message.architectstrials.scroll.inside_challenge"));
        }
        Optional<ChallengeTheme> theme = ChallengeThemes.get(target.theme());
        ServerLevel themeLevel = theme.map(t -> level.getServer().getLevel(t.dimension())).orElse(null);
        if (theme.isEmpty() || themeLevel == null) {
            return Result.failure(Component.translatable("message.architectstrials.scroll.unknown_theme", target.theme().toString()));
        }
        int difficulty = player.getData(ModAttachments.DIFFICULTY).level(target.theme());
        if (difficulty < target.tier()) {
            return Result.failure(Component.translatable("message.architectstrials.scroll.difficulty_too_low", target.tier(), difficulty));
        }
        if (!hasSpace(level, portalPos)) {
            return Result.failure(Component.translatable("message.architectstrials.scroll.no_space"));
        }

        InstanceCreation creation = InstanceManager.create(themeLevel, theme.get(), target.tier(), themeLevel.getRandom());
        if (creation instanceof InstanceCreation.Failure(Component reason)) {
            return Result.failure(reason);
        }
        ChallengeInstance instance = ((InstanceCreation.Success) creation).instance();
        ChallengePortal portal = ChallengePortal.open(level, portalPos, portalYaw, player, instance, stack.copyWithCount(1));
        stack.consume(1, player);
        return Result.success(portal);
    }

    /**
     * Checks for the free 1×2 space a player needs to reach the portal.
     *
     * @param level the level
     * @param pos   the lower block position
     * @return {@code true} if both blocks have no collision
     */
    static boolean hasSpace(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }
}

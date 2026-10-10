package com.gilfort.architectstrials.scroll;

import java.util.Locale;
import java.util.function.Consumer;

import com.gilfort.architectstrials.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The challenge scroll: a single-use item bound to a theme and tier via the {@link ScrollTarget} component.
 * Right-clicking while looking at a block opens a challenge portal in front of that block face.
 * <p>
 * Implemented via {@link #use} with a ray trace instead of {@code useOn}, so the scroll also works for
 * players in Adventure mode.
 */
public class ChallengeScrollItem extends Item {

    /**
     * Creates the item.
     *
     * @param properties the item properties
     */
    public ChallengeScrollItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.scroll.no_target"));
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos portalPos = hit.getBlockPos().relative(hit.getDirection());
        ScrollActivation.Result result = ScrollActivation.activate(serverPlayer, player.getItemInHand(hand), portalPos, player.getYRot() + 180.0F);
        result.message().ifPresent(player::sendOverlayMessage);
        return result.succeeded() ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }

    @Override
    public Component getName(ItemStack stack) {
        ScrollTarget target = stack.get(ModDataComponents.SCROLL_TARGET.get());
        if (target == null) {
            return super.getName(stack);
        }
        Component theme = Component.translatableWithFallback(target.theme().toLanguageKey("dimension"), target.theme().toString());
        return Component.translatable(this.descriptionId + ".named", theme, target.tier());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        if (!stack.has(ModDataComponents.SCROLL_TARGET.get())) {
            builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.blank").withStyle(ChatFormatting.RED));
            return;
        }
        builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.usage").withStyle(ChatFormatting.GRAY));
        ScrollModifiers modifiers = stack.getOrDefault(ModDataComponents.SCROLL_MODIFIERS.get(), ScrollModifiers.NONE);
        if (modifiers.timePercent() != 0.0) {
            builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.modifier.time", percent(modifiers.timePercent()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        modifiers.maxPlayers().ifPresent(players -> builder.accept((players == 0
                ? Component.translatable("tooltip.architectstrials.challenge_scroll.modifier.players_unlimited")
                : Component.translatable("tooltip.architectstrials.challenge_scroll.modifier.players", players)).withStyle(ChatFormatting.DARK_AQUA)));
        if (modifiers.portalOpenPercent() != 0.0) {
            builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.modifier.portal", percent(modifiers.portalOpenPercent()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (modifiers.allowReentry()) {
            builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.modifier.reentry").withStyle(ChatFormatting.DARK_AQUA));
        }
        for (ScrollEffect effect : stack.getOrDefault(ModDataComponents.SCROLL_EFFECTS.get(), ScrollEffects.NONE).entries()) {
            boolean forPlayers = effect.target() == ScrollEffect.Target.PLAYER;
            builder.accept(Component.translatable(forPlayers ? "tooltip.architectstrials.challenge_scroll.effect.player"
                            : "tooltip.architectstrials.challenge_scroll.effect.mobs", effect.describe(context.tickRate()))
                    .withStyle(forPlayers ? ChatFormatting.BLUE : ChatFormatting.RED));
        }
        builder.accept(Component.translatable("tooltip.architectstrials.challenge_scroll.challenge_rules").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Formats a percent modifier with its sign and at most one decimal, e.g. {@code +56.3 %}. */
    private static String percent(double value) {
        String number = value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
        return (value > 0 ? "+" : "") + number;
    }
}

package com.gilfort.architectstrials.command;

import java.util.Collection;
import java.util.List;

import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ScrollTarget;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Commands for challenge scrolls:
 * <ul>
 * <li>{@code /architectstrials scroll give <theme> <tier> [targets]} — gives a scroll bound to a theme and tier</li>
 * </ul>
 */
final class ScrollCommand {

    private ScrollCommand() {
    }

    /**
     * Builds the {@code scroll} sub command tree.
     *
     * @return the literal builder for {@code scroll}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("scroll")
                .then(Commands.literal("give")
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(context -> give(context, List.of(context.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(context -> give(context, EntityArgument.getPlayers(context, "targets")))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        Identifier theme = IdentifierArgument.getId(context, "theme");
        if (ChallengeThemes.get(theme).isEmpty()) {
            throw ThemeCommand.UNKNOWN_THEME.create(theme);
        }
        int tier = IntegerArgumentType.getInteger(context, "tier");
        for (ServerPlayer player : targets) {
            ItemStack scroll = createScroll(theme, tier);
            Component name = scroll.getHoverName();
            if (!player.getInventory().add(scroll)) {
                player.spawnAtLocation(player.level(), scroll);
            }
            context.getSource().sendSuccess(() -> Component.translatable("commands.architectstrials.scroll.give.success",
                    player.getDisplayName(), name), true);
        }
        return targets.size();
    }

    /**
     * Creates a scroll bound to a theme and tier.
     *
     * @param theme the theme id
     * @param tier  the tier
     * @return the scroll stack
     */
    static ItemStack createScroll(Identifier theme, int tier) {
        ItemStack scroll = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        scroll.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(theme, tier));
        return scroll;
    }
}

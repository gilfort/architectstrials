package com.gilfort.architectstrials.command;

import java.util.Collection;

import com.gilfort.architectstrials.rank.PlayerRank;
import com.gilfort.architectstrials.registry.ModAttachments;
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

/**
 * Rank commands, usable by operators and in datapack functions (e.g. advancement rewards):
 * <ul>
 * <li>{@code /architectstrials rank <targets> <theme> set <level>}</li>
 * <li>{@code /architectstrials rank <targets> <theme> add <amount>} (amount may be negative)</li>
 * <li>{@code /architectstrials rank <player> <theme> get} — the result is the level, usable with
 * {@code execute store}</li>
 * </ul>
 */
final class RankCommand {

    private RankCommand() {
    }

    /**
     * Builds the {@code rank} sub command tree.
     *
     * @return the literal builder for {@code rank}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("rank")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .then(Commands.literal("set").then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(context -> change(context, false, IntegerArgumentType.getInteger(context, "level")))))
                                .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> change(context, true, IntegerArgumentType.getInteger(context, "amount")))))
                                .then(Commands.literal("get").executes(RankCommand::get))));
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean relative, int value) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        Identifier theme = theme(context);
        for (ServerPlayer player : targets) {
            PlayerRank rank = player.getData(ModAttachments.RANK);
            int level = relative ? rank.level(theme) + value : value;
            PlayerRank updated = rank.withLevel(theme, level);
            player.setData(ModAttachments.RANK, updated);
            context.getSource().sendSuccess(() -> Component.translatable("commands.architectstrials.rank.set",
                    player.getDisplayName(), theme.toString(), updated.level(theme)), true);
        }
        return targets.size();
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "targets");
        Identifier theme = theme(context);
        int level = player.getData(ModAttachments.RANK).level(theme);
        context.getSource().sendSuccess(() -> Component.translatable("commands.architectstrials.rank.get",
                player.getDisplayName(), theme.toString(), level), false);
        return level;
    }

    private static Identifier theme(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier theme = IdentifierArgument.getId(context, "theme");
        if (ChallengeThemes.get(theme).isEmpty()) {
            throw ThemeCommand.UNKNOWN_THEME.create(theme);
        }
        return theme;
    }
}

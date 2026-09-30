package com.gilfort.architectstrials.command;

import java.util.Collection;

import com.gilfort.architectstrials.difficulty.PlayerDifficulty;
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
 * Difficulty level commands, usable by operators and in datapack functions (e.g. advancement rewards):
 * <ul>
 * <li>{@code /architectstrials difficulty <targets> <theme> set <level>}</li>
 * <li>{@code /architectstrials difficulty <targets> <theme> add <amount>} (amount may be negative)</li>
 * <li>{@code /architectstrials difficulty <player> <theme> get} — the result is the level, usable with
 * {@code execute store}</li>
 * </ul>
 */
final class DifficultyCommand {

    private DifficultyCommand() {
    }

    /**
     * Builds the {@code difficulty} sub command tree.
     *
     * @return the literal builder for {@code difficulty}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("difficulty")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .then(Commands.literal("set").then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(context -> change(context, false, IntegerArgumentType.getInteger(context, "level")))))
                                .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> change(context, true, IntegerArgumentType.getInteger(context, "amount")))))
                                .then(Commands.literal("get").executes(DifficultyCommand::get))));
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean relative, int value) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        Identifier theme = theme(context);
        for (ServerPlayer player : targets) {
            PlayerDifficulty difficulty = player.getData(ModAttachments.DIFFICULTY);
            int level = relative ? difficulty.level(theme) + value : value;
            PlayerDifficulty updated = difficulty.withLevel(theme, level);
            player.setData(ModAttachments.DIFFICULTY, updated);
            context.getSource().sendSuccess(() -> Component.translatable("commands.architectstrials.difficulty.set",
                    player.getDisplayName(), theme.toString(), updated.level(theme)), true);
        }
        return targets.size();
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "targets");
        Identifier theme = theme(context);
        int level = player.getData(ModAttachments.DIFFICULTY).level(theme);
        context.getSource().sendSuccess(() -> Component.translatable("commands.architectstrials.difficulty.get",
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

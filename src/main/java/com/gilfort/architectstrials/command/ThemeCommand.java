package com.gilfort.architectstrials.command;

import java.util.Collection;
import java.util.Set;

import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug commands for challenge themes:
 * <ul>
 * <li>{@code /architectstrials theme list} — lists all loaded themes</li>
 * <li>{@code /architectstrials theme tp <theme>} — teleports the executing player into a theme's dimension</li>
 * </ul>
 */
final class ThemeCommand {

    /** Y level players are teleported to; challenge dimensions are void worlds without terrain. */
    private static final double TELEPORT_Y = 64.0;

    /** Error thrown when a theme id does not match any loaded theme. */
    static final DynamicCommandExceptionType UNKNOWN_THEME = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.architectstrials.theme.unknown", String.valueOf(id)));

    /** Suggests the ids of all loaded themes. */
    static final SuggestionProvider<CommandSourceStack> THEME_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ChallengeThemes.all().stream().map(ChallengeTheme::id), builder);

    private ThemeCommand() {
    }

    /**
     * Builds the {@code theme} sub command tree.
     *
     * @return the literal builder for {@code theme}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("theme")
                .then(Commands.literal("list").executes(ThemeCommand::list))
                .then(Commands.literal("tp")
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(THEME_SUGGESTIONS)
                                .executes(ThemeCommand::teleport)));
    }

    /**
     * Lists all loaded themes with their display names.
     *
     * @param context the command context
     * @return the number of loaded themes
     */
    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<ChallengeTheme> themes = ChallengeThemes.all();
        if (themes.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.list.header", themes.size()), false);
        for (ChallengeTheme theme : themes) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.list.entry",
                    theme.displayName(), theme.id().toString()), false);
        }
        return themes.size();
    }

    /**
     * Teleports the executing player to the origin of the theme's dimension.
     *
     * @param context the command context
     * @return {@code 1} on success
     * @throws CommandSyntaxException if the executor is not a player or the theme is unknown
     */
    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, "theme");
        ChallengeTheme theme = ChallengeThemes.get(id).orElseThrow(() -> UNKNOWN_THEME.create(id));
        ServerLevel level = source.getServer().getLevel(theme.dimension());
        if (level == null) {
            throw UNKNOWN_THEME.create(id);
        }

        player.teleportTo(level, 0.5, TELEPORT_Y, 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.tp.success",
                player.getDisplayName(), theme.displayName()), true);
        if (!player.isCreative() && !player.isSpectator()) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.tp.void_warning"), false);
        }
        return 1;
    }
}

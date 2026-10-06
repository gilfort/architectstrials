package com.gilfort.architectstrials.command;

import java.util.Collection;

import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;
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
import net.minecraft.world.phys.Vec3;

/**
 * Debug commands for challenge themes:
 * <ul>
 * <li>{@code /architectstrials theme list} — lists all loaded themes</li>
 * <li>{@code /architectstrials theme tp <theme>} — teleports the executing player into a theme's dimension,
 * storing the entry point but keeping the game mode</li>
 * </ul>
 */
final class ThemeCommand {


    /** Error thrown when a theme id does not match any loaded theme. */
    static final DynamicCommandExceptionType UNKNOWN_THEME = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.architectstrials.theme.unknown", String.valueOf(id)));

    /** Suggests the ids of all loaded themes. */
    static final SuggestionProvider<CommandSourceStack> THEME_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ChallengeThemes.all().stream().map(ChallengeTheme::id), builder);

    private ThemeCommand() {
    }

    /**
     * Resolves a theme argument to the theme's level.
     *
     * @param context  the command context
     * @param argument the name of the theme argument
     * @return the level of the theme dimension
     * @throws CommandSyntaxException if the theme is unknown or its level does not exist
     */
    static ServerLevel themeLevel(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, argument);
        ChallengeTheme theme = ChallengeThemes.get(id).orElseThrow(() -> UNKNOWN_THEME.create(id));
        ServerLevel level = context.getSource().getServer().getLevel(theme.dimension());
        if (level == null) {
            throw UNKNOWN_THEME.create(id);
        }
        return level;
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
            String tiers = ChallengeStructures.tiers(theme.id()).toString();
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.list.entry",
                    theme.displayName(), theme.id().toString(), tiers), false);
        }
        return themes.size();
    }

    /**
     * Teleports the executing player to the origin of the theme's dimension.
     * <p>
     * Uses {@link ChallengeTravel#enter} so the entry point is stored and {@code /architectstrials exit}
     * returns the player to where they came from. Unlike {@code enter}, the game mode is left unchanged.
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

        ChallengeTravel.enter(player, level, new Vec3(0.5, ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt(), 0.5), player.getYRot(), player.getXRot(), false);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.tp.success",
                player.getDisplayName(), theme.displayName()), true);
        if (!player.isCreative() && !player.isSpectator()) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.tp.void_warning"), false);
        }
        return 1;
    }
}

package com.gilfort.architectstrials.command;

import java.util.Collection;
import java.util.List;

import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Commands for moving players into and out of Architect's Trials dimensions:
 * <ul>
 * <li>{@code /architectstrials enter <theme>} — debug entry into a theme (stores the entry point, Adventure mode)</li>
 * <li>{@code /architectstrials exit [targets]} — returns players to their entry point, or their spawn point</li>
 * </ul>
 */
final class TravelCommand {

    /** Y level used by the debug entry; challenge dimensions are void worlds without terrain. */
    private static final double ENTER_Y = 64.0;

    private TravelCommand() {
    }

    /**
     * Builds the {@code enter} sub command.
     *
     * @return the literal builder for {@code enter}
     */
    static LiteralArgumentBuilder<CommandSourceStack> buildEnter() {
        return Commands.literal("enter")
                .then(Commands.argument("theme", IdentifierArgument.id())
                        .suggests(ThemeCommand.THEME_SUGGESTIONS)
                        .executes(TravelCommand::enter));
    }

    /**
     * Builds the {@code exit} sub command.
     *
     * @return the literal builder for {@code exit}
     */
    static LiteralArgumentBuilder<CommandSourceStack> buildExit() {
        return Commands.literal("exit")
                .executes(context -> exit(context, List.of(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> exit(context, EntityArgument.getPlayers(context, "targets"))));
    }

    /**
     * Enters the executing player into a theme's dimension via {@link ChallengeTravel#enter}.
     *
     * @param context the command context
     * @return {@code 1} on success, {@code 0} if the player already is inside
     * @throws CommandSyntaxException if the executor is not a player or the theme is unknown
     */
    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, "theme");
        ChallengeTheme theme = ChallengeThemes.get(id).orElseThrow(() -> ThemeCommand.UNKNOWN_THEME.create(id));
        ServerLevel level = source.getServer().getLevel(theme.dimension());
        if (level == null) {
            throw ThemeCommand.UNKNOWN_THEME.create(id);
        }
        if (ChallengeTravel.isModDimension(player.level().dimension())) {
            source.sendFailure(Component.translatable("commands.architectstrials.enter.already_inside"));
            return 0;
        }

        ChallengeTravel.enter(player, level, new Vec3(0.5, ENTER_Y, 0.5), player.getYRot(), player.getXRot(), true);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.enter.success",
                player.getDisplayName(), theme.displayName()), true);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.theme.tp.void_warning"), false);
        return 1;
    }

    /**
     * Brings the given players out of Architect's Trials dimensions via {@link ChallengeTravel#exit}.
     * Players outside of these dimensions are skipped with a failure message.
     *
     * @param context the command context
     * @param targets the players to bring out
     * @return the number of players brought out
     */
    private static int exit(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        CommandSourceStack source = context.getSource();
        int count = 0;
        for (ServerPlayer player : targets) {
            if (!ChallengeTravel.isModDimension(player.level().dimension())) {
                source.sendFailure(Component.translatable("commands.architectstrials.exit.not_inside", player.getDisplayName()));
                continue;
            }
            boolean toEntryPoint = ChallengeTravel.exit(player);
            String key = toEntryPoint ? "commands.architectstrials.exit.entry_point" : "commands.architectstrials.exit.spawn_point";
            source.sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
            count++;
        }
        return count;
    }
}

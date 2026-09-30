package com.gilfort.architectstrials.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug commands for challenge instances:
 * <ul>
 * <li>{@code /architectstrials instance list} — lists all instances with state, remaining time and participants</li>
 * <li>{@code /architectstrials instance close <id>} — returns the participants and removes the instance</li>
 * <li>{@code /architectstrials instance create <theme> <tier> [join]} — creates an instance and places its structure;
 * with {@code join} the executing player enters it at a random spawn point</li>
 * </ul>
 */
final class InstanceCommand {

    private InstanceCommand() {
    }

    /**
     * Builds the {@code instance} sub command tree.
     *
     * @return the literal builder for {@code instance}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("instance")
                .then(Commands.literal("list").executes(InstanceCommand::list))
                .then(Commands.literal("close").then(Commands.argument("id", UuidArgument.uuid())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(allInstances(context.getSource().getServer())
                                .map(instance -> instance.id().toString()), builder))
                        .executes(InstanceCommand::close)))
                .then(Commands.literal("create")
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(context -> create(context, false))
                                        .then(Commands.literal("join").executes(context -> create(context, true))))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long now = ChallengeClock.now(server);
        List<ChallengeInstance> instances = allInstances(server).toList();
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.instance.list.header", instances.size()), false);
        for (ChallengeInstance instance : instances) {
            long seconds = Math.max(0L, instance.deadline() - now) / ChallengeClock.TICKS_PER_SECOND;
            String remaining = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.instance.list.entry",
                    instance.id().toString().substring(0, 8), instance.theme().toString(), instance.tier(), instance.slot(),
                    Component.translatable("architectstrials.instance.state." + instance.state().name().toLowerCase(Locale.ROOT)),
                    remaining, instance.participants().size()), false);
        }
        return instances.size();
    }

    private static int close(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID id = UuidArgument.getUuid(context, "id");
        for (ChallengeTheme theme : ChallengeThemes.all()) {
            ServerLevel level = source.getServer().getLevel(theme.dimension());
            if (level == null) {
                continue;
            }
            Optional<ChallengeInstance> instance = InstanceManager.data(level).get(id);
            if (instance.isPresent()) {
                for (UUID participant : instance.get().participants()) {
                    ServerPlayer player = source.getServer().getPlayerList().getPlayer(participant);
                    if (player != null && player.level() == level) {
                        ChallengeTravel.returnToEntryPoint(player);
                    }
                }
                InstanceManager.close(level, id);
                source.sendSuccess(() -> Component.translatable("commands.architectstrials.instance.close.success", id.toString()), true);
                return 1;
            }
        }
        source.sendFailure(Component.translatable("commands.architectstrials.instance.close.unknown", id.toString()));
        return 0;
    }

    private static Stream<ChallengeInstance> allInstances(MinecraftServer server) {
        return ChallengeThemes.all().stream()
                .map(theme -> server.getLevel(theme.dimension()))
                .filter(level -> level != null)
                .flatMap(level -> InstanceManager.data(level).all().stream());
    }

    private static int create(CommandContext<CommandSourceStack> context, boolean join) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = ThemeCommand.themeLevel(context, "theme");
        ChallengeTheme theme = ChallengeThemes.get(IdentifierArgument.getId(context, "theme")).orElseThrow();
        int tier = IntegerArgumentType.getInteger(context, "tier");

        InstanceCreation result = InstanceManager.create(level, theme, tier, level.getRandom(), InstanceManager.defaultTimeLimitTicks());
        if (result instanceof InstanceCreation.Failure(Component reason)) {
            source.sendFailure(reason);
            return 0;
        }
        ChallengeInstance instance = ((InstanceCreation.Success) result).instance();
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.instance.create.success",
                instance.structure().toString(), instance.slot(),
                instance.origin().getX(), instance.origin().getY(), instance.origin().getZ(),
                instance.id().toString()), true);
        if (!instance.ready()) {
            source.sendFailure(Component.translatable("commands.architectstrials.instance.create.not_ready"));
        } else if (join) {
            InstanceManager.join(source.getPlayerOrException(), level, instance);
        }
        return 1;
    }
}

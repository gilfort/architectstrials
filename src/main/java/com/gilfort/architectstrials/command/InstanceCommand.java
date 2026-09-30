package com.gilfort.architectstrials.command;

import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Debug commands for challenge instances:
 * <ul>
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
                .then(Commands.literal("create")
                        .then(Commands.argument("theme", IdentifierArgument.id())
                                .suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(context -> create(context, false))
                                        .then(Commands.literal("join").executes(context -> create(context, true))))));
    }

    private static int create(CommandContext<CommandSourceStack> context, boolean join) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = ThemeCommand.themeLevel(context, "theme");
        ChallengeTheme theme = ChallengeThemes.get(IdentifierArgument.getId(context, "theme")).orElseThrow();
        int tier = IntegerArgumentType.getInteger(context, "tier");

        InstanceCreation result = InstanceManager.create(level, theme, tier, level.getRandom());
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

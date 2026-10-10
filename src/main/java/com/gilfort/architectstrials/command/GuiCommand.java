package com.gilfort.architectstrials.command;

import com.gilfort.architectstrials.browser.ChallengeBrowser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /architectstrials gui} — opens the challenge browser (US-40) for the executing player.
 */
final class GuiCommand {

    private GuiCommand() {
    }

    /**
     * Builds the {@code gui} sub command.
     *
     * @return the literal builder for {@code gui}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("gui").executes(context -> {
            ChallengeBrowser.open(context.getSource().getPlayerOrException());
            return 1;
        });
    }
}

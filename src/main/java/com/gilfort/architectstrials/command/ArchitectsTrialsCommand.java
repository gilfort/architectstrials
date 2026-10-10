package com.gilfort.architectstrials.command;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Root of the {@code /architectstrials} command tree, also registered under the short alias {@code /at}. All sub
 * commands require game master permissions.
 * <p>
 * The tree is registered twice instead of using a Brigadier redirect, because redirects break the vanilla tab
 * completion of commands with arguments.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ArchitectsTrialsCommand {

    /** Literal name of the root command. */
    public static final String ROOT = "architectstrials";

    /** Short alias of the root command. */
    public static final String ALIAS = "at";

    private ArchitectsTrialsCommand() {
    }

    /**
     * Registers the command tree.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(tree(ROOT));
        event.getDispatcher().register(tree(ALIAS));
    }

    /**
     * Builds the complete command tree under the given root name.
     *
     * @param name the root literal
     * @return the command tree
     */
    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(ThemeCommand.build())
                .then(TravelCommand.buildEnter())
                .then(TravelCommand.buildExit())
                .then(SlotCommand.build())
                .then(InstanceCommand.build())
                .then(ScrollCommand.build())
                .then(MarkerCommand.build())
                .then(RankCommand.build())
                .then(EditorCommand.build())
                .then(StructureCommand.build())
                .then(GuiCommand.build());
    }
}

package com.gilfort.architectstrials.command;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Root of the {@code /architectstrials} command tree. All sub commands require game master permissions.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ArchitectsTrialsCommand {

    /** Literal name of the root command. */
    public static final String ROOT = "architectstrials";

    private ArchitectsTrialsCommand() {
    }

    /**
     * Registers the command tree.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ROOT)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(ThemeCommand.build())
                .then(TravelCommand.buildEnter())
                .then(TravelCommand.buildExit())
                .then(SlotCommand.build())
                .then(InstanceCommand.build())
                .then(ScrollCommand.build())
                .then(MarkerCommand.build())
                .then(DifficultyCommand.build()));
    }
}

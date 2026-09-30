package com.gilfort.architectstrials.command;

import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotData;
import com.gilfort.architectstrials.slot.SlotManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * Debug commands for the slot grid of theme dimensions:
 * <ul>
 * <li>{@code /architectstrials slot list <theme>} — lists occupied and clearing slots</li>
 * <li>{@code /architectstrials slot allocate <theme>} — allocates the next free slot</li>
 * <li>{@code /architectstrials slot free <theme> <index>} — releases and clears a slot</li>
 * </ul>
 */
final class SlotCommand {

    private SlotCommand() {
    }

    /**
     * Builds the {@code slot} sub command tree.
     *
     * @return the literal builder for {@code slot}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("slot")
                .then(Commands.literal("list").then(themeArgument().executes(SlotCommand::list)))
                .then(Commands.literal("allocate").then(themeArgument().executes(SlotCommand::allocate)))
                .then(Commands.literal("free").then(themeArgument()
                        .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(SlotCommand::free))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> themeArgument() {
        return Commands.argument("theme", IdentifierArgument.id()).suggests(ThemeCommand.THEME_SUGGESTIONS);
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = ThemeCommand.themeLevel(context, "theme");
        SlotData data = SlotManager.data(level);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.slot.list.header",
                data.occupied().size(), data.clearing().size()), false);
        for (int index : data.occupied()) {
            sendEntry(source, SlotManager.slot(level, index), "commands.architectstrials.slot.list.occupied");
        }
        for (int index : data.clearing()) {
            sendEntry(source, SlotManager.slot(level, index), "commands.architectstrials.slot.list.clearing");
        }
        return data.occupied().size();
    }

    private static int allocate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = ThemeCommand.themeLevel(context, "theme");
        return SlotManager.allocate(level).map(slot -> {
            sendEntry(source, slot, "commands.architectstrials.slot.allocate.success");
            return 1;
        }).orElseGet(() -> {
            source.sendFailure(Component.translatable("commands.architectstrials.slot.allocate.limit_reached"));
            return 0;
        });
    }

    private static int free(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = ThemeCommand.themeLevel(context, "theme");
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!SlotManager.release(level, index)) {
            source.sendFailure(Component.translatable("commands.architectstrials.slot.free.not_occupied", index));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.slot.free.success", index), true);
        return 1;
    }

    private static void sendEntry(CommandSourceStack source, Slot slot, String key) {
        source.sendSuccess(() -> Component.translatable(key, slot.index(), slot.centerX(), slot.centerZ()), false);
    }
}

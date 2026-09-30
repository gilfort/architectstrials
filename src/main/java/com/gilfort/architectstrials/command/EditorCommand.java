package com.gilfort.architectstrials.command;

import java.util.HashMap;
import java.util.Map;

import com.gilfort.architectstrials.editor.EditorDimension;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands of the editor dimension (leaving uses {@code /architectstrials exit}):
 * <ul>
 * <li>{@code /architectstrials editor enter} — enter the shared editor, game mode unchanged</li>
 * <li>{@code /architectstrials editor clear} — asks for confirmation</li>
 * <li>{@code /architectstrials editor clear confirm} — within 30 seconds: empties the editor</li>
 * <li>{@code save} / {@code load} — see {@link StructureCommand}</li>
 * </ul>
 */
final class EditorCommand {

    /** Time window for confirming a clear, in server ticks. */
    private static final int CONFIRM_TICKS = 30 * 20;

    /** Pending clear requests by command source name, mapped to the server tick they expire at. */
    private static final Map<String, Integer> PENDING_CLEARS = new HashMap<>();

    private EditorCommand() {
    }

    /**
     * Builds the {@code editor} sub command tree.
     *
     * @return the literal builder for {@code editor}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return StructureCommand.addEditorCommands(Commands.literal("editor")
                .then(Commands.literal("enter").executes(EditorCommand::enter))
                .then(Commands.literal("clear")
                        .executes(EditorCommand::requestClear)
                        .then(Commands.literal("confirm").executes(EditorCommand::confirmClear))));
    }

    private static int enter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        if (player.level() == editor) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.already_inside"));
            return 0;
        }
        EditorDimension.enter(player, editor);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.enter.success"), false);
        return 1;
    }

    private static int requestClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PENDING_CLEARS.put(source.getTextName(), source.getServer().getTickCount() + CONFIRM_TICKS);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.clear.warning"), false);
        return 1;
    }

    private static int confirmClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Integer expiresAt = PENDING_CLEARS.remove(source.getTextName());
        if (expiresAt == null || expiresAt < source.getServer().getTickCount()) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.clear.no_request"));
            return 0;
        }
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.clear.started"), true);
        EditorDimension.clear(editor, cleared -> source.sendSuccess(
                () -> Component.translatable("commands.architectstrials.editor.clear.done"), true));
        return 1;
    }

    private static ServerLevel editorLevel(CommandSourceStack source) {
        ServerLevel editor = source.getServer().getLevel(EditorDimension.KEY);
        if (editor == null) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.missing"));
        }
        return editor;
    }
}

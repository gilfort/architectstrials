package com.gilfort.architectstrials.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.editor.EditorDimension;
import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.editor.SelectionToolItem;
import com.gilfort.architectstrials.editor.WorldImport;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Commands of the editor dimension (leaving uses {@code /architectstrials exit}):
 * <ul>
 * <li>{@code /architectstrials editor enter} — enter the shared editor, game mode unchanged</li>
 * <li>{@code /architectstrials editor clear} — asks for confirmation</li>
 * <li>{@code /architectstrials editor clear confirm} — within 30 seconds: empties the editor</li>
 * <li>{@code /architectstrials editor import} — copies the area selected with the selection tool into the empty
 * editor; large areas ask for {@code confirm}</li>
 * <li>{@code save} / {@code load} — see {@link StructureCommand}</li>
 * </ul>
 */
final class EditorCommand {

    /** Time window for confirming a clear, in server ticks. */
    private static final int CONFIRM_TICKS = 30 * 20;

    /** Pending clear requests by command source name, mapped to the server tick they expire at. */
    private static final Map<String, Integer> PENDING_CLEARS = new HashMap<>();

    /** Pending large imports by command source name. */
    private static final Map<String, PendingImport> PENDING_IMPORTS = new HashMap<>();

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
                        .then(Commands.literal("confirm").executes(EditorCommand::confirmClear)))
                .then(Commands.literal("import")
                        .executes(EditorCommand::requestImport)
                        .then(Commands.literal("confirm").executes(EditorCommand::confirmImport))));
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

    private static int requestImport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<ImportTarget> target = importTarget(source, player);
        if (target.isEmpty()) {
            return 0;
        }
        int blocks = EditorCapture.countBlocks(target.get().level(), target.get().box());
        if (blocks > WorldImport.CONFIRM_THRESHOLD) {
            PENDING_IMPORTS.put(source.getTextName(), new PendingImport(target.get().level().dimension(), target.get().box(),
                    source.getServer().getTickCount() + CONFIRM_TICKS));
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.import.warning", blocks), false);
            return 1;
        }
        return runImport(source, target.get());
    }

    private static int confirmImport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PendingImport pending = PENDING_IMPORTS.remove(source.getTextName());
        Optional<ImportTarget> target = importTarget(source, player);
        if (target.isEmpty()) {
            return 0;
        }
        if (pending == null || pending.expiresAt() < source.getServer().getTickCount()
                || !pending.dimension().equals(target.get().level().dimension()) || !pending.box().equals(target.get().box())) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.import.no_request"));
            return 0;
        }
        return runImport(source, target.get());
    }

    private static int runImport(CommandSourceStack source, ImportTarget target) {
        WorldImport.Result result = WorldImport.importInto(target.level(), target.box(), target.editor());
        EditorDimension.freeStuckPlayers(target.editor());
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.import.success", result.copiedBlocks(),
                result.spawners()), true);
        if (!result.missingEggs().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.editor.import.missing_eggs",
                    String.join(", ", result.missingEggs().stream().map(Identifier::toString).toList())), false);
        }
        return Math.max(1, result.copiedBlocks());
    }

    /**
     * Validates an import: a complete selection outside the mod dimensions, at most 128 x 128 blocks wide, low
     * enough to fit above the placement height of the editor, and an empty editor. Sends the failure message otherwise.
     */
    private static Optional<ImportTarget> importTarget(CommandSourceStack source, ServerPlayer player) {
        Selection selection = SelectionToolItem.find(player).get(ModDataComponents.SELECTION.get());
        Optional<BoundingBox> box = selection == null ? Optional.empty() : selection.box();
        if (box.isEmpty()) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.import.no_selection"));
            return Optional.empty();
        }
        ServerLevel level = source.getServer().getLevel(selection.dimension());
        if (level == null || ChallengeTravel.isModDimension(selection.dimension())) {
            source.sendFailure(Component.translatable("message.architectstrials.selection.mod_dimension"));
            return Optional.empty();
        }
        if (SelectionToolItem.tooLarge(box.get())) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.import.too_large", box.get().getXSpan(),
                    box.get().getZSpan(), Slot.MAX_STRUCTURE_SIZE, Slot.MAX_STRUCTURE_SIZE));
            return Optional.empty();
        }
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return Optional.empty();
        }
        int maxHeight = editor.getMaxY() - ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt() + 1;
        if (box.get().getYSpan() > maxHeight) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.import.too_high", box.get().getYSpan(), maxHeight));
            return Optional.empty();
        }
        if (!EditorCapture.isEmpty(editor, EditorDimension.area(editor))) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.load.not_empty"));
            return Optional.empty();
        }
        return Optional.of(new ImportTarget(level, box.get(), editor));
    }

    /** A validated import: source level, selected box and editor level. */
    private record ImportTarget(ServerLevel level, BoundingBox box, ServerLevel editor) {
    }

    /** An import waiting for confirmation. */
    private record PendingImport(ResourceKey<Level> dimension, BoundingBox box, int expiresAt) {
    }

    private static ServerLevel editorLevel(CommandSourceStack source) {
        ServerLevel editor = source.getServer().getLevel(EditorDimension.KEY);
        if (editor == null) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.missing"));
        }
        return editor;
    }
}

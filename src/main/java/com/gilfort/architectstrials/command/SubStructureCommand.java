package com.gilfort.architectstrials.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.editor.EditorDimension;
import com.gilfort.architectstrials.editor.StructureLibrary;
import com.gilfort.architectstrials.sub.SubStructure;
import com.gilfort.architectstrials.sub.SubStructures;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The {@code sub} branches of the structure commands (US-32): {@code /at editor save sub <id> [overwrite]},
 * {@code /at editor load sub <id>}, {@code /at structure list sub} and {@code /at structure delete sub <id> [confirm]}.
 * Sub structures are stored in the managed datapack under {@code data/<ns>/architectstrials/sub/} and
 * {@code data/<ns>/structure/sub/}; they have no theme or tier and are never drawn as challenges.
 */
final class SubStructureCommand {

    private static final int CONFIRM_TICKS = 20 * 30;
    private static final Map<String, PendingDelete> PENDING_DELETES = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(SubStructures.all().keySet(), builder);

    private record PendingDelete(Identifier id, int expiresAt) {
    }

    private SubStructureCommand() {
    }

    /** @return {@code sub <id> [overwrite]} for {@code /at editor save} */
    static LiteralArgumentBuilder<CommandSourceStack> save() {
        return Commands.literal("sub").then(Commands.argument("sub_id", IdentifierArgument.id())
                .executes(context -> save(context, false))
                .then(Commands.literal("overwrite").executes(context -> save(context, true))));
    }

    /** @return {@code sub <id>} for {@code /at editor load} */
    static LiteralArgumentBuilder<CommandSourceStack> load() {
        return Commands.literal("sub").then(Commands.argument("sub_id", IdentifierArgument.id()).suggests(ID_SUGGESTIONS)
                .executes(SubStructureCommand::load));
    }

    /** @return {@code sub} for {@code /at structure list} */
    static LiteralArgumentBuilder<CommandSourceStack> list() {
        return Commands.literal("sub").executes(SubStructureCommand::list);
    }

    /** @return {@code sub <id> [confirm]} for {@code /at structure delete} */
    static LiteralArgumentBuilder<CommandSourceStack> delete() {
        return Commands.literal("sub").then(Commands.argument("sub_id", IdentifierArgument.id()).suggests(ID_SUGGESTIONS)
                .executes(SubStructureCommand::requestDelete)
                .then(Commands.literal("confirm").executes(SubStructureCommand::confirmDelete)));
    }

    private static int save(CommandContext<CommandSourceStack> context, boolean overwrite) {
        CommandSourceStack source = context.getSource();
        Identifier id = IdentifierArgument.getId(context, "sub_id");
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        try {
            Optional<SubStructure> existing = StructureLibrary.readSubMetadata(source.getServer(), id);
            if (existing.isPresent() && !overwrite) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.save.exists", id.toString()));
                return 0;
            }
            Optional<EditorCapture.Captured> captured = EditorCapture.capture(editor, EditorDimension.area(editor));
            if (captured.isEmpty()) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.save.empty"));
                return 0;
            }
            EditorCapture.Validation validation = EditorCapture.validateSub(editor, captured.get());
            validation.warnings().forEach(warning -> source.sendSuccess(() -> warning, false));
            if (!validation.valid()) {
                validation.errors().forEach(source::sendFailure);
                return 0;
            }
            SubStructure metadata = new SubStructure(SubStructure.templateId(id), existing.flatMap(SubStructure::name),
                    Optional.of(source.getTextName()), Optional.of(System.currentTimeMillis()));
            StructureLibrary.writeSub(source.getServer(), id, captured.get().template(), metadata);
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.error("Could not save sub structure {}", id, e);
            source.sendFailure(Component.translatable("commands.architectstrials.structure.io_error", e.getMessage()));
            return 0;
        }
        reloadThen(source, Component.translatable("commands.architectstrials.sub_structure.save.success", id.toString()));
        return 1;
    }

    private static int load(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Identifier id = IdentifierArgument.getId(context, "sub_id");
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        Optional<StructureTemplate> template = SubStructures.get(id)
                .flatMap(sub -> source.getServer().getStructureTemplateManager().get(sub.structure()));
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.unknown", id.toString()));
            return 0;
        }
        if (!EditorCapture.isEmpty(editor, EditorDimension.area(editor))) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.load.not_empty"));
            return 0;
        }
        EditorCapture.place(editor, template.get());
        EditorDimension.freeStuckPlayers(editor);
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.load.success", id.toString()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var entries = SubStructures.all().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.sub_structure.list.header", entries.size()), false);
        for (var entry : entries) {
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.sub_structure.list.entry", entry.getKey().toString(),
                    entry.getValue().name().orElse("-")), false);
        }
        return entries.size();
    }

    private static int requestDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Identifier id = IdentifierArgument.getId(context, "sub_id");
        PENDING_DELETES.put(source.getTextName(), new PendingDelete(id, source.getServer().getTickCount() + CONFIRM_TICKS));
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.sub_structure.delete.warning", id.toString()), false);
        return 1;
    }

    private static int confirmDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Identifier id = IdentifierArgument.getId(context, "sub_id");
        PendingDelete pending = PENDING_DELETES.remove(source.getTextName());
        if (pending == null || !pending.id().equals(id) || pending.expiresAt() < source.getServer().getTickCount()) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.delete.no_request"));
            return 0;
        }
        try {
            if (!StructureLibrary.deleteSub(source.getServer(), id)) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.not_managed", id.toString()));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.io_error", e.getMessage()));
            return 0;
        }
        reloadThen(source, Component.translatable("commands.architectstrials.structure.delete.success", id.toString()));
        return 1;
    }

    private static void reloadThen(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.reloading"), false);
        StructureLibrary.reload(source.getServer()).thenRun(() -> source.sendSuccess(() -> message, true));
    }

    private static ServerLevel editorLevel(CommandSourceStack source) {
        ServerLevel editor = source.getServer().getLevel(EditorDimension.KEY);
        if (editor == null) {
            source.sendFailure(Component.translatable("commands.architectstrials.editor.missing"));
        }
        return editor;
    }
}

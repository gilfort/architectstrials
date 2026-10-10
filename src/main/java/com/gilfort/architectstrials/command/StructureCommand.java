package com.gilfort.architectstrials.command;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.editor.EditorDimension;
import com.gilfort.architectstrials.editor.EditorLoading;
import com.gilfort.architectstrials.editor.EditorState;
import com.gilfort.architectstrials.editor.StructureLibrary;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.StructureValidation;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Commands that save structures from the editor into the structure pool and manage them:
 * <ul>
 * <li>{@code /architectstrials editor save <theme> <tier> <id> [overwrite]} — saves everything in the editor</li>
 * <li>{@code /architectstrials editor load <theme> <tier> <id>} — loads a structure into the empty editor</li>
 * <li>{@code /architectstrials structure list [theme] [tier]}</li>
 * <li>{@code /architectstrials structure validate [theme] [tier]} — reports missing blocks, items, entity types,
 * loot tables and mob effects (e.g. after a mod was removed)</li>
 * <li>{@code /architectstrials structure set <theme> <tier> <id> weight <n> | rotation <bool> | game_mode <adventure|survival>
 * | name <text>}</li>
 * <li>{@code /architectstrials structure delete <theme> <tier> <id>} (+ {@code confirm} within 30 seconds)</li>
 * </ul>
 * Saving, editing and deleting work on the managed datapack {@link StructureLibrary} and reload datapacks
 * afterwards, so changes are live immediately.
 */
final class StructureCommand {

    private static final int CONFIRM_TICKS = 30 * 20;

    /** Maximum number of problems listed per structure by {@code validate}. */
    private static final int VALIDATE_LINES = 10;
    private static final Map<String, PendingDelete> PENDING_DELETES = new HashMap<>();

    private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.architectstrials.structure.invalid_id", String.valueOf(id)));

    private static final SuggestionProvider<CommandSourceStack> ID_SUGGESTIONS = (context, builder) -> {
        Identifier theme = IdentifierArgument.getId(context, "theme");
        int tier = IntegerArgumentType.getInteger(context, "tier");
        return SharedSuggestionProvider.suggest(ChallengeStructures.pool(theme, tier).stream()
                .map(id -> id.getPath().substring(id.getPath().lastIndexOf('/') + 1)), builder);
    };

    private record PendingDelete(StructureLibrary.Entry entry, int expiresAt) {
    }

    private StructureCommand() {
    }

    /**
     * Adds {@code save} and {@code load} (also for sub structures, US-32) to the {@code editor} command.
     *
     * @param editor the editor literal
     * @return the same builder
     */
    static LiteralArgumentBuilder<CommandSourceStack> addEditorCommands(LiteralArgumentBuilder<CommandSourceStack> editor) {
        return editor
                .then(Commands.literal("save").then(SubStructureCommand.save()).then(entryArguments(false, id -> id
                        .executes(context -> save(context, false))
                        .then(Commands.literal("overwrite").executes(context -> save(context, true))))))
                .then(Commands.literal("load").then(SubStructureCommand.load())
                        .then(entryArguments(true, id -> id.executes(StructureCommand::load))));
    }

    /**
     * Builds the {@code structure} sub command tree.
     *
     * @return the literal builder for {@code structure}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("structure")
                .then(Commands.literal("list")
                        .executes(context -> list(context, null, 0))
                        .then(SubStructureCommand.list())
                        .then(Commands.argument("theme", IdentifierArgument.id()).suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .executes(context -> list(context, IdentifierArgument.getId(context, "theme"), 0))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, IdentifierArgument.getId(context, "theme"),
                                                IntegerArgumentType.getInteger(context, "tier"))))))
                .then(Commands.literal("validate")
                        .executes(context -> validate(context, null, 0))
                        .then(Commands.argument("theme", IdentifierArgument.id()).suggests(ThemeCommand.THEME_SUGGESTIONS)
                                .executes(context -> validate(context, IdentifierArgument.getId(context, "theme"), 0))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(context -> validate(context, IdentifierArgument.getId(context, "theme"),
                                                IntegerArgumentType.getInteger(context, "tier"))))))
                .then(Commands.literal("set").then(entryArguments(true, id -> id
                        .then(Commands.literal("weight").then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                .executes(context -> set(context, s -> copy(s, s.name(), IntegerArgumentType.getInteger(context, "weight"), s.rotation())))))
                        .then(Commands.literal("rotation").then(Commands.argument("rotation", BoolArgumentType.bool())
                                .executes(context -> set(context, s -> copy(s, s.name(), s.weight(), BoolArgumentType.getBool(context, "rotation"))))))
                        .then(Commands.literal("game_mode")
                                .then(Commands.literal("adventure").executes(context -> set(context, s -> withGameMode(s, GameType.ADVENTURE))))
                                .then(Commands.literal("survival").executes(context -> set(context, s -> withGameMode(s, GameType.SURVIVAL)))))
                        .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> set(context, s -> copy(s, Optional.of(StringArgumentType.getString(context, "name")), s.weight(), s.rotation()))))))))
                .then(Commands.literal("delete").then(SubStructureCommand.delete())
                        .then(entryArguments(true, id -> id
                                .executes(StructureCommand::requestDelete)
                                .then(Commands.literal("confirm").executes(StructureCommand::confirmDelete)))));
    }

    /**
     * Builds {@code <theme> <tier> <id>} with the given continuation on the id argument.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> entryArguments(boolean suggestExisting,
            UnaryOperator<com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>> continuation) {
        var id = Commands.argument("id", StringArgumentType.word());
        if (suggestExisting) {
            id = id.suggests(ID_SUGGESTIONS);
        }
        return Commands.argument("theme", IdentifierArgument.id()).suggests(ThemeCommand.THEME_SUGGESTIONS)
                .then(Commands.argument("tier", IntegerArgumentType.integer(1)).then(continuation.apply(id)));
    }

    private static StructureLibrary.Entry entry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier theme = IdentifierArgument.getId(context, "theme");
        if (ChallengeThemes.get(theme).isEmpty()) {
            throw ThemeCommand.UNKNOWN_THEME.create(theme);
        }
        String name = StringArgumentType.getString(context, "id");
        if (!Identifier.isValidPath(name) || name.contains("/")) {
            throw INVALID_ID.create(name);
        }
        return new StructureLibrary.Entry(theme, IntegerArgumentType.getInteger(context, "tier"), name);
    }

    private static int save(CommandContext<CommandSourceStack> context, boolean overwrite) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StructureLibrary.Entry entry = entry(context);
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        try {
            Optional<ChallengeStructure> existing = StructureLibrary.readMetadata(source.getServer(), entry);
            if (existing.isPresent() && !overwrite) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.save.exists", entry.name()));
                return 0;
            }
            Optional<EditorCapture.Captured> captured = EditorCapture.capture(editor, EditorDimension.area(editor));
            if (captured.isEmpty()) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.save.empty"));
                return 0;
            }
            EditorCapture.Validation validation = EditorCapture.validate(editor, captured.get());
            validation.warnings().forEach(warning -> source.sendSuccess(() -> warning, false));
            if (!validation.valid()) {
                validation.errors().forEach(source::sendFailure);
                return 0;
            }
            ChallengeStructure metadata = new ChallengeStructure(entry.theme(), entry.tier(), entry.structureId(),
                    existing.flatMap(ChallengeStructure::name), Optional.of(source.getTextName()), Optional.of(System.currentTimeMillis()),
                    existing.map(ChallengeStructure::weight).orElse(1), existing.map(ChallengeStructure::rotation).orElse(false),
                    existing.map(ChallengeStructure::gameMode).orElse(GameType.ADVENTURE),
                    existing.map(ChallengeStructure::playerEffects).orElse(List.of()),
                    existing.map(ChallengeStructure::playerAttributes).orElse(List.of()),
                    existing.flatMap(ChallengeStructure::oreGeneration));
            StructureLibrary.write(source.getServer(), entry, captured.get().template(), metadata);
            EditorState.of(source.getServer()).setLast(Optional.of(new EditorState.StructureRef(false, entry.metadataId())));
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.error("Could not save structure {}", entry, e);
            source.sendFailure(Component.translatable("commands.architectstrials.structure.io_error", e.getMessage()));
            return 0;
        }
        reloadThen(source, Component.translatable("commands.architectstrials.structure.save.success",
                entry.name(), entry.theme().toString(), entry.tier()));
        return 1;
    }

    private static int load(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StructureLibrary.Entry entry = entry(context);
        ServerLevel editor = editorLevel(source);
        if (editor == null) {
            return 0;
        }
        Optional<ChallengeStructure> structure = ChallengeStructures.get(entry.metadataId());
        Optional<StructureTemplate> template = structure.flatMap(s -> source.getServer().getStructureTemplateManager().get(s.structure()));
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.unknown", entry.name()));
            return 0;
        }
        if (!EditorCapture.isEmpty(editor, EditorDimension.area(editor))) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.load.not_empty"));
            return 0;
        }
        EditorLoading.place(editor, template.get(), new EditorState.StructureRef(false, entry.metadataId()));
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.load.success", entry.name()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, Identifier theme, int tier) {
        CommandSourceStack source = context.getSource();
        var entries = ChallengeStructures.all().entrySet().stream()
                .filter(e -> theme == null || e.getValue().theme().equals(theme))
                .filter(e -> tier == 0 || e.getValue().tier() == tier)
                .sorted(Map.Entry.comparingByKey())
                .toList();
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.list.header", entries.size()), false);
        for (var e : entries) {
            ChallengeStructure s = e.getValue();
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.list.entry",
                    e.getKey().toString(), s.name().orElse("-"), s.tier(), s.weight(), String.valueOf(s.rotation()), s.gameMode().getName()), false);
        }
        return entries.size();
    }

    /**
     * Checks the templates of all matching structures for missing blocks, items, entity types, loot tables and mob
     * effects (e.g. after a mod was removed). Only structures with problems are listed.
     */
    private static int validate(CommandContext<CommandSourceStack> context, Identifier theme, int tier) {
        CommandSourceStack source = context.getSource();
        var entries = ChallengeStructures.all().entrySet().stream()
                .filter(e -> theme == null || e.getValue().theme().equals(theme))
                .filter(e -> tier == 0 || e.getValue().tier() == tier)
                .sorted(Map.Entry.comparingByKey())
                .toList();
        int broken = 0;
        for (var e : entries) {
            Map<String, Integer> problems = StructureValidation.validate(source.getServer(), e.getValue());
            if (problems.isEmpty()) {
                continue;
            }
            broken++;
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.validate.structure",
                    e.getKey().toString(), problems.size()), false);
            problems.entrySet().stream().limit(VALIDATE_LINES).forEach(problem -> source.sendSuccess(() -> Component.translatable(
                    "commands.architectstrials.structure.validate.problem", problem.getKey(), problem.getValue()), false));
            if (problems.size() > VALIDATE_LINES) {
                source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.validate.more",
                        problems.size() - VALIDATE_LINES), false);
            }
        }
        int brokenCount = broken;
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.validate.summary", entries.size(), brokenCount), false);
        return brokenCount;
    }

    private static int set(CommandContext<CommandSourceStack> context, UnaryOperator<ChallengeStructure> change) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StructureLibrary.Entry entry = entry(context);
        try {
            Optional<ChallengeStructure> existing = StructureLibrary.readMetadata(source.getServer(), entry);
            if (existing.isEmpty()) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.not_managed", entry.name()));
                return 0;
            }
            StructureLibrary.writeMetadata(source.getServer(), entry, change.apply(existing.get()));
        } catch (IOException e) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.io_error", e.getMessage()));
            return 0;
        }
        reloadThen(source, Component.translatable("commands.architectstrials.structure.set.success", entry.name()));
        return 1;
    }

    private static int requestDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StructureLibrary.Entry entry = entry(context);
        PENDING_DELETES.put(source.getTextName(), new PendingDelete(entry, source.getServer().getTickCount() + CONFIRM_TICKS));
        source.sendSuccess(() -> Component.translatable("commands.architectstrials.structure.delete.warning", entry.name()), false);
        return 1;
    }

    private static int confirmDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StructureLibrary.Entry entry = entry(context);
        PendingDelete pending = PENDING_DELETES.remove(source.getTextName());
        if (pending == null || !pending.entry().equals(entry) || pending.expiresAt() < source.getServer().getTickCount()) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.delete.no_request"));
            return 0;
        }
        try {
            if (!StructureLibrary.delete(source.getServer(), entry)) {
                source.sendFailure(Component.translatable("commands.architectstrials.structure.not_managed", entry.name()));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Component.translatable("commands.architectstrials.structure.io_error", e.getMessage()));
            return 0;
        }
        reloadThen(source, Component.translatable("commands.architectstrials.structure.delete.success", entry.name()));
        return 1;
    }

    private static ChallengeStructure copy(ChallengeStructure s, Optional<String> name, int weight, boolean rotation) {
        return s.with(name, weight, rotation, s.gameMode());
    }

    private static ChallengeStructure withGameMode(ChallengeStructure s, GameType gameMode) {
        return s.with(s.name(), s.weight(), s.rotation(), gameMode);
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

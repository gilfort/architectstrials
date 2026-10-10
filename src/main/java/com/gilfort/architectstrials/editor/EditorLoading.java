package com.gilfort.architectstrials.editor;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.sub.SubStructure;
import com.gilfort.architectstrials.sub.SubStructures;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Loads stored structures into the shared editor (US-40) — the "Load to editor" action of the challenge browser.
 * An empty editor is loaded right away; a non-empty one is only cleared when the request confirms it, because
 * clearing destroys the work of everyone building there.
 */
public final class EditorLoading {

    private EditorLoading() {
    }

    /** Outcome of a load request. */
    public sealed interface Result {
    }

    /** The structure was loaded (or clearing has started and it will be loaded afterwards). */
    public record Loaded() implements Result {
    }

    /**
     * The editor is not empty and the request did not confirm clearing it.
     *
     * @param builders names of the players currently in the editor
     * @param last     the structure last loaded into or saved from the editor, if known
     */
    public record Busy(List<String> builders, Optional<EditorState.StructureRef> last) implements Result {
    }

    /**
     * The structure could not be loaded.
     *
     * @param message the reason
     */
    public record Failed(Component message) implements Result {
    }

    /**
     * Loads a stored structure into the editor and moves the player into it.
     *
     * @param player     the requesting player
     * @param structure  the structure to load
     * @param clearFirst {@code true} if the player confirmed that a non-empty editor may be cleared
     * @return the outcome
     */
    public static Result load(ServerPlayer player, EditorState.StructureRef structure, boolean clearFirst) {
        MinecraftServer server = player.level().getServer();
        ServerLevel editor = server.getLevel(EditorDimension.KEY);
        if (editor == null) {
            return new Failed(Component.translatable("commands.architectstrials.editor.missing"));
        }
        Optional<StructureTemplate> template = template(server, structure);
        if (template.isEmpty()) {
            return new Failed(Component.translatable("commands.architectstrials.structure.unknown", structure.id().toString()));
        }
        if (EditorCapture.isEmpty(editor, EditorDimension.area(editor))) {
            placeAndEnter(player, editor, template.get(), structure);
            return new Loaded();
        }
        if (!clearFirst) {
            return new Busy(editor.players().stream().map(builder -> builder.getName().getString()).sorted().toList(), EditorState.of(server).last());
        }
        player.sendSystemMessage(Component.translatable("commands.architectstrials.editor.clear.started"));
        EditorDimension.clear(editor, cleared -> {
            if (!player.hasDisconnected()) {
                placeAndEnter(player, cleared, template.get(), structure);
            }
        });
        return new Loaded();
    }

    /**
     * Places a template into the editor, frees builders stuck in blocks and remembers the structure.
     *
     * @param editor    the editor level
     * @param template  the template
     * @param structure the stored structure the template belongs to
     */
    public static void place(ServerLevel editor, StructureTemplate template, EditorState.StructureRef structure) {
        EditorCapture.place(editor, template);
        EditorDimension.freeStuckPlayers(editor);
        EditorState.of(editor.getServer()).setLast(Optional.of(structure));
    }

    private static void placeAndEnter(ServerPlayer player, ServerLevel editor, StructureTemplate template, EditorState.StructureRef structure) {
        place(editor, template, structure);
        if (player.level() != editor) {
            EditorDimension.enter(player, editor);
        }
        player.sendSystemMessage(Component.translatable("commands.architectstrials.structure.load.success", structure.id().toString()));
    }

    private static Optional<StructureTemplate> template(MinecraftServer server, EditorState.StructureRef structure) {
        Optional<Identifier> templateId = structure.sub()
                ? SubStructures.get(structure.id()).map(SubStructure::structure)
                : ChallengeStructures.get(structure.id()).map(ChallengeStructure::structure);
        return templateId.flatMap(id -> server.getStructureTemplateManager().get(id));
    }
}

package com.gilfort.architectstrials.editor;

import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent state of the shared editor (US-40): the structure that was last loaded into it or saved from it, so
 * the challenge browser can tell builders what is in the editor before it gets cleared. Clearing or importing
 * forgets it.
 */
public final class EditorState extends SavedData {

    /** Saved data type; stored as {@code data/architectstrials/editor.dat} in the overworld. */
    public static final SavedDataType<EditorState> TYPE = new SavedDataType<>(ArchitectsTrials.id("editor"), EditorState::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    StructureRef.CODEC.optionalFieldOf("last").forGetter(state -> state.last)
            ).apply(instance, EditorState::new)));

    private Optional<StructureRef> last;

    /** Creates an empty state. */
    public EditorState() {
        this(Optional.empty());
    }

    private EditorState(Optional<StructureRef> last) {
        this.last = last;
    }

    /**
     * Returns the editor state of a server.
     *
     * @param server the server
     * @return the state, created on first access
     */
    public static EditorState of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** @return the structure last loaded into or saved from the editor, if any */
    public Optional<StructureRef> last() {
        return this.last;
    }

    /**
     * Records the structure last loaded into or saved from the editor.
     *
     * @param structure the structure, or empty to forget it (clear, import)
     */
    public void setLast(Optional<StructureRef> structure) {
        if (!this.last.equals(structure)) {
            this.last = structure;
            this.setDirty();
        }
    }

    /**
     * A stored structure: a challenge structure (by metadata id) or a sub structure (by id).
     *
     * @param sub {@code true} for a sub structure
     * @param id  the metadata id of the challenge structure, or the sub structure id
     */
    public record StructureRef(boolean sub, Identifier id) {

        /** Codec. */
        public static final Codec<StructureRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("sub", false).forGetter(StructureRef::sub),
                Identifier.CODEC.fieldOf("id").forGetter(StructureRef::id)
        ).apply(instance, StructureRef::new));
    }
}

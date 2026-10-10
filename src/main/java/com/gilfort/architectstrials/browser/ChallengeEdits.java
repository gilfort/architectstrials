package com.gilfort.architectstrials.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorState;
import com.gilfort.architectstrials.editor.StructureLibrary;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.InstancePlacements;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollModifiers;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.OreGeneration;
import com.gilfort.architectstrials.sub.SubStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of editing in the challenge browser (US-42): saving changed settings of a challenge structure,
 * deleting structures of the managed datapack and starting test runs of exactly one structure.
 * <p>
 * Saving is guarded by a revision (a hash of the metadata the browser showed): if the file changed in the
 * meantime — another operator, {@code /at structure set} — the save is rejected instead of silently overwriting.
 */
public final class ChallengeEdits {

    /** Longest accepted display name. */
    public static final int MAX_NAME_LENGTH = 64;

    private ChallengeEdits() {
    }

    /** Outcome of a save request. */
    public enum SaveStatus {
        /** Written; the datapacks are reloading. */
        SAVED,
        /** The metadata changed since the browser loaded it. */
        CONFLICT,
        /** At least one field is invalid; nothing was written. */
        INVALID,
        /** The structure is not in the managed datapack (or no longer exists). */
        NOT_EDITABLE
    }

    /**
     * A validation problem of one field.
     *
     * @param field   the field key ({@code name}, {@code ore_generation}, …; empty for the whole metadata)
     * @param message the message
     */
    public record FieldError(String field, Component message) {
    }

    /**
     * Result of a save request.
     *
     * @param status the status
     * @param errors the field errors ({@link SaveStatus#INVALID} only)
     */
    public record SaveResult(SaveStatus status, List<FieldError> errors) {
    }

    /**
     * Computes the revision of metadata: a hash of its canonical JSON encoding.
     *
     * @param server    the server
     * @param structure the metadata
     * @return the revision
     */
    public static String revision(MinecraftServer server, ChallengeStructure structure) {
        String json = ChallengeStructure.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), structure)
                .result().map(JsonElement::toString).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(json.hashCode());
        }
    }

    /**
     * Validates and writes edited settings of a challenge structure. Only the editable fields are taken from the
     * edit (name, weight, rotation, game mode, run settings, effects, attributes, ore generation); theme, tier,
     * template, author and creation time stay as stored.
     *
     * @param server   the server
     * @param id       the metadata id
     * @param revision the revision the edit is based on
     * @param json     the edited metadata as JSON
     * @return the result; on {@link SaveStatus#SAVED} the datapacks are being reloaded
     */
    public static SaveResult save(MinecraftServer server, Identifier id, String revision, String json) {
        Optional<ChallengeStructure> current;
        try {
            current = ChallengeStructures.source(id).filter(StructureLibrary.PACK_ID::equals).isPresent()
                    ? StructureLibrary.readMetadata(server, id) : Optional.empty();
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.warn("Could not read metadata of {}", id, e);
            current = Optional.empty();
        }
        if (current.isEmpty()) {
            return new SaveResult(SaveStatus.NOT_EDITABLE, List.of());
        }
        Merge merge = merge(server, current.get(), revision, json);
        if (merge.merged().isEmpty()) {
            return merge.result();
        }
        try {
            StructureLibrary.writeMetadata(server, id, merge.merged().get());
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.error("Could not write metadata of {}", id, e);
            return new SaveResult(SaveStatus.INVALID, List.of(new FieldError("", Component.translatable(
                    "commands.architectstrials.structure.io_error", e.getMessage()))));
        }
        StructureLibrary.reload(server);
        return merge.result();
    }

    /**
     * Result of checking an edit against the stored metadata.
     *
     * @param result the save result
     * @param merged the metadata to write; present only for {@link SaveStatus#SAVED}
     */
    public record Merge(SaveResult result, Optional<ChallengeStructure> merged) {
    }

    /**
     * Checks an edit against the stored metadata without touching any file: revision, JSON, field validation; on
     * success merges the editable fields into the stored metadata.
     *
     * @param server   the server
     * @param current  the stored metadata
     * @param revision the revision the edit is based on
     * @param json     the edited metadata as JSON
     * @return the result and, if valid, the merged metadata
     */
    public static Merge merge(MinecraftServer server, ChallengeStructure current, String revision, String json) {
        if (!revision(server, current).equals(revision)) {
            return new Merge(new SaveResult(SaveStatus.CONFLICT, List.of()), Optional.empty());
        }
        DataResult<ChallengeStructure> parsed;
        try {
            parsed = ChallengeStructure.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json));
        } catch (JsonParseException e) {
            parsed = DataResult.error(e::getMessage);
        }
        if (parsed.isError()) {
            String message = parsed.error().map(DataResult.Error::message).orElse("?");
            return new Merge(new SaveResult(SaveStatus.INVALID, List.of(new FieldError("",
                    Component.translatable("message.architectstrials.browser.save.invalid", message)))), Optional.empty());
        }
        ChallengeStructure edit = parsed.getOrThrow();
        List<FieldError> errors = new ArrayList<>();
        Optional<String> name = edit.name().map(String::trim).filter(text -> !text.isEmpty());
        if (name.isPresent() && name.get().length() > MAX_NAME_LENGTH) {
            errors.add(new FieldError("name", Component.translatable("message.architectstrials.browser.save.name_too_long", MAX_NAME_LENGTH)));
        }
        Optional<OreGeneration> ores = edit.oreGeneration();
        if (ores.isPresent() && server.registryAccess().lookupOrThrow(Registries.BIOME).get(ores.get().biome()).isEmpty()) {
            errors.add(new FieldError("ore_generation", Component.translatable("message.architectstrials.browser.save.unknown_biome",
                    ores.get().biome().identifier().toString())));
        }
        if (!errors.isEmpty()) {
            return new Merge(new SaveResult(SaveStatus.INVALID, errors), Optional.empty());
        }
        ChallengeStructure stored = current;
        ChallengeStructure merged = new ChallengeStructure(stored.theme(), stored.tier(), stored.structure(), name, stored.author(), stored.created(),
                edit.weight(), edit.rotation(), edit.gameMode(), edit.playerEffects(), edit.playerAttributes(), edit.oreGeneration(), edit.run(),
                edit.mobEffects());
        return new Merge(new SaveResult(SaveStatus.SAVED, List.of()), Optional.of(merged));
    }

    /**
     * Deletes a challenge or sub structure of the managed datapack and reloads the datapacks.
     *
     * @param server    the server
     * @param structure the structure
     * @return the message for the player
     */
    public static Component delete(MinecraftServer server, EditorState.StructureRef structure) {
        Optional<String> source = structure.sub() ? SubStructures.source(structure.id()) : ChallengeStructures.source(structure.id());
        if (source.filter(StructureLibrary.PACK_ID::equals).isEmpty()) {
            return Component.translatable("commands.architectstrials.structure.not_managed", structure.id().toString());
        }
        try {
            boolean deleted = structure.sub() ? StructureLibrary.deleteSub(server, structure.id()) : StructureLibrary.delete(server, structure.id());
            if (!deleted) {
                return Component.translatable("commands.architectstrials.structure.not_managed", structure.id().toString());
            }
        } catch (IOException e) {
            return Component.translatable("commands.architectstrials.structure.io_error", e.getMessage());
        }
        StructureLibrary.reload(server);
        return Component.translatable("commands.architectstrials.structure.delete.success", structure.id().toString());
    }

    /**
     * Starts a test run: creates an instance of exactly this challenge structure with its own run settings and lets
     * the player join once it is placed.
     *
     * @param player the player
     * @param id     the metadata id of the challenge structure
     * @return empty on success, otherwise the failure message
     */
    public static Optional<Component> testRun(ServerPlayer player, Identifier id) {
        MinecraftServer server = player.level().getServer();
        Optional<ChallengeStructure> structure = ChallengeStructures.get(id);
        Optional<ChallengeTheme> theme = structure.flatMap(found -> ChallengeThemes.get(found.theme()));
        ServerLevel level = theme.map(found -> server.getLevel(found.dimension())).orElse(null);
        if (structure.isEmpty() || theme.isEmpty() || level == null) {
            return Optional.of(Component.translatable("commands.architectstrials.structure.unknown", id.toString()));
        }
        InstanceCreation creation = InstanceManager.create(level, theme.get(), structure.get().tier(), level.getRandom(), Optional.of(id),
                ScrollModifiers.NONE, ScrollEffects.NONE);
        if (creation instanceof InstanceCreation.Failure(Component reason)) {
            return Optional.of(reason);
        }
        ChallengeInstance instance = ((InstanceCreation.Success) creation).instance();
        UUID playerId = player.getUUID();
        player.sendSystemMessage(Component.translatable("message.architectstrials.browser.test_run", id.toString()));
        InstancePlacements.whenReady(level, instance.id(), ready -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                InstanceManager.join(online, level, ready);
            }
        });
        return Optional.empty();
    }
}

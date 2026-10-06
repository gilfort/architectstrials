package com.gilfort.architectstrials.editor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.sub.SubStructure;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The datapack managed by the editor, {@code <world>/datapacks/architectstrials_structures/}. Saved structures
 * are written here in the layout of the structure pool, so modpack creators only copy this one folder:
 * <pre>
 * data/&lt;ns&gt;/structure/challenges/&lt;theme&gt;/tier_&lt;n&gt;/&lt;id&gt;.nbt
 * data/&lt;ns&gt;/architectstrials/challenge/&lt;theme&gt;/tier_&lt;n&gt;/&lt;id&gt;.json
 * data/&lt;ns&gt;/structure/sub/&lt;id&gt;.nbt                  (sub structures, US-32)
 * data/&lt;ns&gt;/architectstrials/sub/&lt;id&gt;.json
 * </pre>
 * {@code <ns>} and {@code <theme>} are the namespace and path of the theme id.
 */
public final class StructureLibrary {

    /** Folder name of the managed datapack. */
    public static final String PACK_NAME = "architectstrials_structures";

    /** Pack repository id of the managed datapack. */
    public static final String PACK_ID = "file/" + PACK_NAME;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StructureLibrary() {
    }

    /**
     * Identifies one structure of the library.
     *
     * @param theme the theme id
     * @param tier  the tier
     * @param name  the structure id within theme and tier (a resource path)
     */
    public record Entry(Identifier theme, int tier, String name) {

        /** @return the id of the structure template ({@code <ns>:challenges/<theme>/tier_<n>/<name>}) */
        public Identifier structureId() {
            return Identifier.fromNamespaceAndPath(this.theme.getNamespace(), "challenges/" + this.relativePath());
        }

        /** @return the id of the metadata file as seen by the structure pool ({@code <ns>:<theme>/tier_<n>/<name>}) */
        public Identifier metadataId() {
            return Identifier.fromNamespaceAndPath(this.theme.getNamespace(), this.relativePath());
        }

        private String relativePath() {
            return this.theme.getPath() + "/tier_" + this.tier + "/" + this.name;
        }
    }

    /**
     * Returns the root folder of the managed datapack.
     *
     * @param server the server
     * @return the pack folder
     */
    public static Path packRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
    }

    /** @return the template file of an entry */
    static Path structureFile(MinecraftServer server, Entry entry) {
        Identifier id = entry.structureId();
        return packRoot(server).resolve("data").resolve(id.getNamespace()).resolve("structure").resolve(id.getPath() + ".nbt");
    }

    /** @return the metadata file of an entry */
    static Path metadataFile(MinecraftServer server, Entry entry) {
        Identifier id = entry.metadataId();
        return packRoot(server).resolve("data").resolve(id.getNamespace()).resolve("architectstrials").resolve("challenge")
                .resolve(id.getPath() + ".json");
    }

    /**
     * Reads the metadata of an entry from the managed datapack.
     *
     * @param server the server
     * @param entry  the entry
     * @return the metadata, or empty if the entry is not in the managed datapack
     * @throws IOException if the file exists but cannot be read
     */
    public static Optional<ChallengeStructure> readMetadata(MinecraftServer server, Entry entry) throws IOException {
        Path file = metadataFile(server, entry);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return ChallengeStructure.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).result();
        }
    }

    /**
     * Writes (or replaces) the metadata of an entry.
     *
     * @param server    the server
     * @param entry     the entry
     * @param structure the metadata
     * @throws IOException if writing fails
     */
    public static void writeMetadata(MinecraftServer server, Entry entry, ChallengeStructure structure) throws IOException {
        ensurePack(server);
        Path file = metadataFile(server, entry);
        Files.createDirectories(file.getParent());
        JsonElement json = ChallengeStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow();
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(json, writer);
        }
    }

    /**
     * Writes (or replaces) template and metadata of an entry.
     *
     * @param server    the server
     * @param entry     the entry
     * @param template  the structure template
     * @param structure the metadata
     * @throws IOException if writing fails
     */
    public static void write(MinecraftServer server, Entry entry, StructureTemplate template, ChallengeStructure structure) throws IOException {
        ensurePack(server);
        Path file = structureFile(server, entry);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(template.save(new CompoundTag()), file);
        writeMetadata(server, entry, structure);
    }

    /**
     * Deletes template and metadata of an entry from the managed datapack.
     *
     * @param server the server
     * @param entry  the entry
     * @return {@code true} if anything was deleted
     * @throws IOException if deleting fails
     */
    public static boolean delete(MinecraftServer server, Entry entry) throws IOException {
        boolean deletedStructure = Files.deleteIfExists(structureFile(server, entry));
        boolean deletedMetadata = Files.deleteIfExists(metadataFile(server, entry));
        return deletedStructure || deletedMetadata;
    }

    /** @return the template file of a sub structure (US-32): {@code data/<ns>/structure/sub/<id>.nbt} */
    static Path subStructureFile(MinecraftServer server, Identifier id) {
        Identifier template = SubStructure.templateId(id);
        return packRoot(server).resolve("data").resolve(template.getNamespace()).resolve("structure").resolve(template.getPath() + ".nbt");
    }

    /** @return the metadata file of a sub structure (US-32): {@code data/<ns>/architectstrials/sub/<id>.json} */
    static Path subMetadataFile(MinecraftServer server, Identifier id) {
        return packRoot(server).resolve("data").resolve(id.getNamespace()).resolve("architectstrials").resolve("sub")
                .resolve(id.getPath() + ".json");
    }

    /**
     * Reads the metadata of a sub structure from the managed datapack.
     *
     * @param server the server
     * @param id     the sub structure id
     * @return the metadata, or empty if it is not in the managed datapack
     * @throws IOException if the file exists but cannot be read
     */
    public static Optional<SubStructure> readSubMetadata(MinecraftServer server, Identifier id) throws IOException {
        Path file = subMetadataFile(server, id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return SubStructure.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).result();
        }
    }

    /**
     * Writes (or replaces) template and metadata of a sub structure.
     *
     * @param server    the server
     * @param id        the sub structure id
     * @param template  the structure template
     * @param structure the metadata
     * @throws IOException if writing fails
     */
    public static void writeSub(MinecraftServer server, Identifier id, StructureTemplate template, SubStructure structure) throws IOException {
        ensurePack(server);
        Path file = subStructureFile(server, id);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(template.save(new CompoundTag()), file);
        Path metadata = subMetadataFile(server, id);
        Files.createDirectories(metadata.getParent());
        JsonElement json = SubStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow();
        try (Writer writer = Files.newBufferedWriter(metadata)) {
            GSON.toJson(json, writer);
        }
    }

    /**
     * Deletes template and metadata of a sub structure from the managed datapack.
     *
     * @param server the server
     * @param id     the sub structure id
     * @return {@code true} if anything was deleted
     * @throws IOException if deleting fails
     */
    public static boolean deleteSub(MinecraftServer server, Identifier id) throws IOException {
        boolean deletedStructure = Files.deleteIfExists(subStructureFile(server, id));
        boolean deletedMetadata = Files.deleteIfExists(subMetadataFile(server, id));
        return deletedStructure || deletedMetadata;
    }

    /**
     * Enables the managed datapack (if needed) and reloads all datapacks, so the structure pool picks up changes.
     *
     * @param server the server
     * @return completes once the reload has finished
     */
    public static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (!selected.contains(PACK_ID)) {
            selected.add(PACK_ID);
        }
        return server.reloadResources(selected);
    }

    /**
     * Creates the pack folder and its {@code pack.mcmeta} if missing.
     */
    private static void ensurePack(MinecraftServer server) throws IOException {
        Path root = packRoot(server);
        Path meta = root.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return;
        }
        Files.createDirectories(root);
        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        String json = """
                {
                  "pack": {
                    "description": {
                      "translate": "pack.architectstrials.structures.description",
                      "fallback": "Architect's Trials: structures saved in the editor"
                    },
                    "min_format": %d,
                    "max_format": %d
                  }
                }
                """.formatted(format.major(), format.major());
        Files.writeString(meta, json);
    }
}

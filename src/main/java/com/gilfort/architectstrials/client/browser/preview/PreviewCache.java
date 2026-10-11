package com.gilfort.architectstrials.client.browser.preview;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.browser.PreviewNetwork;
import com.gilfort.architectstrials.editor.EditorState;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Client cache of the structure previews (US-43). A picture is keyed by the template's content hash, the viewing
 * direction and the active resource packs, so an unchanged structure is rendered once and then loaded from
 * {@code <gamedir>/architectstrials/previews/}; a re-saved structure has a new hash and is rendered again.
 * <p>
 * Missing pictures go through: request the template from the server → tesselate it on a worker thread → render it on
 * the render thread (one picture per {@link #tick}) → save the PNG. Textures live until the browser closes.
 */
public final class PreviewCache {

    /** State of one picture. */
    public enum State {
        /** Looking for the picture on disk. */
        LOADING_DISK,
        /** Waiting for the template from the server. */
        LOADING_TEMPLATE,
        /** Reducing the template to its visible faces (worker thread). */
        LOADING_MESH,
        /** Waiting to be rendered or being rendered. */
        LOADING_RENDER,
        /** Ready to draw. */
        READY,
        /** The server could not send the template (missing file or too large). */
        FAILED_TEMPLATE,
        /** The template arrived but could not be turned into a picture. */
        FAILED_RENDER;

        /** @return {@code true} while the picture is still on its way */
        public boolean loading() {
            return this.ordinal() <= LOADING_RENDER.ordinal();
        }

        /** @return {@code true} if there will be no picture */
        public boolean failed() {
            return this == FAILED_TEMPLATE || this == FAILED_RENDER;
        }
    }

    /**
     * The status of a picture.
     *
     * @param state   the state
     * @param texture the texture ({@link State#READY} only)
     */
    public record Status(State state, @Nullable Identifier texture) {

        static Status of(State state) {
            return new Status(state, null);
        }
    }

    private static final Map<String, Status> PICTURES = new HashMap<>();
    private static final Map<String, CompletableFuture<StructureMesh>> MESHES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Set<String> FAILED_TEMPLATES = new HashSet<>();
    private static final Deque<String> RENDER_QUEUE = new ArrayDeque<>();
    private static final Map<String, Integer> QUEUED_DIRECTIONS = new HashMap<>();
    private static final Map<String, String> QUEUED_HASHES = new HashMap<>();
    private static int textureCounter;

    private PreviewCache() {
    }

    /**
     * Returns the picture of a structure from one direction, starting whatever is needed to get it.
     *
     * @param structure the structure
     * @param hash      the template hash from the snapshot
     * @param direction the viewing direction (0–3)
     * @return the status
     */
    public static Status get(EditorState.StructureRef structure, String hash, int direction) {
        if (hash.isEmpty()) {
            return Status.of(State.FAILED_TEMPLATE);
        }
        String key = key(hash, direction);
        Status status = PICTURES.get(key);
        if (status != null) {
            return status;
        }
        PICTURES.put(key, Status.of(State.LOADING_DISK));
        Path file = file(key);
        CompletableFuture.supplyAsync(() -> readImage(file), Util.backgroundExecutor()).thenAcceptAsync(image -> {
            if (!PICTURES.containsKey(key)) {
                image.ifPresent(NativeImage::close);
                return;
            }
            if (image.isPresent()) {
                ready(key, image.get());
            } else {
                queueRender(structure, hash, direction, key);
            }
        }, Minecraft.getInstance());
        return Status.of(State.LOADING_DISK);
    }

    private static void queueRender(EditorState.StructureRef structure, String hash, int direction, String key) {
        if (FAILED_TEMPLATES.contains(hash)) {
            PICTURES.put(key, Status.of(State.FAILED_TEMPLATE));
            return;
        }
        CompletableFuture<StructureMesh> mesh = MESHES.get(hash);
        if (mesh == null && REQUESTED.add(hash)) {
            ClientPacketDistributor.sendToServer(new PreviewNetwork.RequestTemplate(structure));
        }
        PICTURES.put(key, Status.of(mesh == null ? State.LOADING_TEMPLATE : mesh.isDone() ? State.LOADING_RENDER : State.LOADING_MESH));
        QUEUED_DIRECTIONS.put(key, direction);
        QUEUED_HASHES.put(key, hash);
        RENDER_QUEUE.add(key);
    }

    /**
     * Receives a template from the server and tesselates it on a worker thread.
     *
     * @param data the template data
     */
    public static void onTemplate(PreviewNetwork.TemplateData data) {
        String hash = data.hash();
        REQUESTED.remove(hash);
        if (data.data().length == 0 || hash.isEmpty()) {
            markFailed(hash);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Holder<Biome> plains = minecraft.level.registryAccess().lookupOrThrow(Registries.BIOME).get(Biomes.PLAINS).<Holder<Biome>>map(holder -> holder)
                .orElse(null);
        byte[] bytes = data.data();
        setLoading(hash, State.LOADING_MESH);
        MESHES.put(hash, CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = new ByteArrayInputStream(bytes)) {
                CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                StructureTemplate template = new StructureTemplate();
                template.load(BuiltInRegistries.BLOCK, tag);
                return StructureMesh.build(template, minecraft.getModelManager().getBlockStateModelSet(), minecraft.getBlockColors(), plains);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the preview template", e);
            }
        }, Util.backgroundExecutor()));
    }

    /** Renders at most one waiting picture whose mesh is ready; call once per client tick from the browser. */
    public static void tick() {
        for (int i = 0; i < RENDER_QUEUE.size(); i++) {
            String key = RENDER_QUEUE.poll();
            String hash = QUEUED_HASHES.get(key);
            CompletableFuture<StructureMesh> mesh = hash == null ? null : MESHES.get(hash);
            if (hash == null || !PICTURES.containsKey(key)) {
                continue;
            }
            if (FAILED_TEMPLATES.contains(hash)) {
                PICTURES.put(key, Status.of(State.FAILED_TEMPLATE));
                continue;
            }
            if (mesh == null || !mesh.isDone()) {
                RENDER_QUEUE.add(key);
                continue;
            }
            PICTURES.put(key, Status.of(State.LOADING_RENDER));
            QUEUED_HASHES.remove(key);
            int direction = QUEUED_DIRECTIONS.remove(key);
            StructureMesh built;
            try {
                built = mesh.join();
            } catch (RuntimeException e) {
                ArchitectsTrials.LOGGER.warn("Could not build the preview mesh of template {}", hash, e);
                PICTURES.replaceAll((other, status) -> other.startsWith(hash + "_") && status.state().loading()
                        ? Status.of(State.FAILED_RENDER) : status);
                MESHES.remove(hash);
                return;
            }
            try {
                PreviewRenderer.render(built, direction, image -> {
                    saveImage(file(key), image);
                    if (PICTURES.containsKey(key)) {
                        ready(key, image);
                    } else {
                        image.close();
                    }
                });
            } catch (RuntimeException e) {
                ArchitectsTrials.LOGGER.warn("Could not render the preview of template {}", hash, e);
                PICTURES.put(key, Status.of(State.FAILED_RENDER));
            }
            return;
        }
    }

    private static void markFailed(String hash) {
        FAILED_TEMPLATES.add(hash);
        PICTURES.replaceAll((key, status) -> key.startsWith(hash + "_") && status.state().loading() ? Status.of(State.FAILED_TEMPLATE) : status);
    }

    private static void setLoading(String hash, State state) {
        PICTURES.replaceAll((key, status) -> key.startsWith(hash + "_") && status.state().loading() ? Status.of(state) : status);
    }

    private static void ready(String key, NativeImage image) {
        Identifier id = ArchitectsTrials.id("preview/" + textureCounter++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Preview " + key, image));
        PICTURES.put(key, new Status(State.READY, id));
    }

    /** Releases all textures and forgets meshes; called when the browser closes. Pictures on disk stay. */
    public static void clear() {
        for (Status status : PICTURES.values()) {
            if (status.texture() != null) {
                Minecraft.getInstance().getTextureManager().release(status.texture());
            }
        }
        PICTURES.clear();
        MESHES.clear();
        REQUESTED.clear();
        FAILED_TEMPLATES.clear();
        RENDER_QUEUE.clear();
        QUEUED_DIRECTIONS.clear();
        QUEUED_HASHES.clear();
    }

    /** @return the cache key of a picture: template hash, direction and active resource packs */
    private static String key(String hash, int direction) {
        String packs = String.join(",", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
        return hash + "_" + direction + "_" + shortHash(packs);
    }

    private static String shortHash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** @return the cache directory {@code <gamedir>/architectstrials/previews} */
    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(ArchitectsTrials.MOD_ID).resolve("previews");
    }

    private static Path file(String key) {
        return directory().resolve(key + ".png");
    }

    private static Optional<NativeImage> readImage(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream stream = Files.newInputStream(file)) {
            return Optional.of(NativeImage.read(stream));
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.warn("Could not read cached preview {}", file, e);
            return Optional.empty();
        }
    }

    private static void saveImage(Path file, NativeImage image) {
        try {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.warn("Could not save preview {}", file, e);
        }
    }
}

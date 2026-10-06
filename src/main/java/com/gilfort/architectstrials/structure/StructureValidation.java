package com.gilfort.architectstrials.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Checks stored structures for content that no longer exists, e.g. after a mod was removed (US-27).
 * <p>
 * The raw template file is read, not the loaded template: vanilla turns unknown blocks into air and drops unknown
 * items while loading, so the original ids are only visible in the file. Reported are missing blocks, items
 * (container contents, marker data, loot setups), entity types (spawn eggs, spawners, template entities), loot
 * tables and mob effects.
 */
public final class StructureValidation {

    private StructureValidation() {
    }

    /**
     * Validates the template of a challenge structure.
     *
     * @param server    the server
     * @param structure the structure metadata
     * @return the problems found, as description → number of occurrences; empty if the structure is clean
     */
    public static Map<String, Integer> validate(MinecraftServer server, ChallengeStructure structure) {
        Map<String, Integer> problems = new TreeMap<>();
        Optional<CompoundTag> data = readTemplate(server, structure.structure());
        if (data.isEmpty()) {
            problems.put("template file not found: " + structure.structure(), 1);
            return problems;
        }
        new Scan(server, problems).template(data.get());
        return problems;
    }

    private static Optional<CompoundTag> readTemplate(MinecraftServer server, Identifier id) {
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
        try {
            Optional<Resource> resource = server.getResourceManager().getResource(file);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return Optional.of(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
                }
            }
            Path generated = server.getWorldPath(LevelResource.GENERATED_DIR).resolve(id.getNamespace()).resolve("structures")
                    .resolve(id.getPath() + ".nbt");
            if (Files.isRegularFile(generated)) {
                try (InputStream stream = Files.newInputStream(generated)) {
                    return Optional.of(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
                }
            }
        } catch (IOException exception) {
            ArchitectsTrials.LOGGER.warn("Could not read structure template {}", id, exception);
        }
        return Optional.empty();
    }

    /** One scan over a template's NBT, collecting problems. */
    private record Scan(MinecraftServer server, Map<String, Integer> problems) {

        void template(CompoundTag data) {
            for (Tag palette : data.getListOrEmpty("palette")) {
                this.paletteEntry(palette);
            }
            for (Tag palettes : data.getListOrEmpty("palettes")) {
                if (palettes instanceof ListTag list) {
                    list.forEach(this::paletteEntry);
                }
            }
            for (Tag block : data.getListOrEmpty("blocks")) {
                if (block instanceof CompoundTag info) {
                    info.getCompound("nbt").ifPresent(this::walk);
                }
            }
            for (Tag entity : data.getListOrEmpty("entities")) {
                if (entity instanceof CompoundTag info) {
                    info.getCompound("nbt").ifPresent(nbt -> {
                        nbt.getString("id").ifPresent(id -> this.check(BuiltInRegistries.ENTITY_TYPE, id, "entity"));
                        this.walk(nbt);
                    });
                }
            }
        }

        private void paletteEntry(Tag entry) {
            if (entry instanceof CompoundTag state) {
                state.getString("Name").or(() -> state.getString("id")).ifPresent(id -> this.check(BuiltInRegistries.BLOCK, id, "block"));
            }
        }

        /** Walks block entity or entity data and checks every id it recognizes. */
        private void walk(Tag tag) {
            if (tag instanceof ListTag list) {
                list.forEach(this::walk);
                return;
            }
            if (!(tag instanceof CompoundTag compound)) {
                return;
            }
            Optional<String> id = compound.getString("id");
            if (id.isPresent() && (compound.contains("count") || compound.contains("Count"))) {
                this.check(BuiltInRegistries.ITEM, id.get(), "item");
            }
            for (String key : compound.keySet()) {
                Tag value = compound.get(key);
                String lower = key.toLowerCase(Locale.ROOT);
                if (value instanceof CompoundTag entity && (lower.endsWith("entity_data") || lower.equals("entitytag") || lower.equals("entity"))) {
                    entity.getString("id").ifPresent(type -> this.check(BuiltInRegistries.ENTITY_TYPE, type, "entity"));
                }
                if (value != null && value.getId() == Tag.TAG_STRING && (lower.contains("loot_table") || lower.equals("loottable") || lower.equals("table"))) {
                    compound.getString(key).ifPresent(this::lootTable);
                }
                if (value != null && value.getId() == Tag.TAG_STRING && lower.equals("effect")) {
                    compound.getString(key).ifPresent(effect -> this.check(BuiltInRegistries.MOB_EFFECT, effect, "mob effect"));
                }
                if (value instanceof ListTag effects && (lower.equals("custom_effects") || lower.equals("active_effects"))) {
                    effects.forEach(effect -> {
                        if (effect instanceof CompoundTag instance) {
                            instance.getString("id").ifPresent(type -> this.check(BuiltInRegistries.MOB_EFFECT, type, "mob effect"));
                        }
                    });
                }
                this.walk(value);
            }
        }

        private void check(Registry<?> registry, String id, String kind) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null || !registry.containsKey(parsed)) {
                this.problems.merge("missing " + kind + ": " + id, 1, Integer::sum);
            }
        }

        private void lootTable(String id) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null || this.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, parsed)) == LootTable.EMPTY) {
                this.problems.merge("missing loot table: " + id, 1, Integer::sum);
            }
        }
    }
}

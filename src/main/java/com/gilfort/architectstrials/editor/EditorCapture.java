package com.gilfort.architectstrials.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.structure.PaintingPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Turns what was built in the editor into a structure template and back.
 * <p>
 * Capturing takes everything inside the given area, trimmed to the blocks actually built (grown to include kept
 * entities such as item frames on outer walls); Editor Platform blocks are ignored. Non-player entities such as item frames, armor stands and paintings are included, mobs are not
 * (enemies are placed with spawn markers).
 */
public final class EditorCapture {

    private EditorCapture() {
    }

    /**
     * Result of validating a captured structure.
     *
     * @param errors   problems that prevent saving
     * @param warnings hints that do not prevent saving
     */
    public record Validation(List<Component> errors, List<Component> warnings) {

        /** @return {@code true} if the structure may be saved */
        public boolean valid() {
            return this.errors.isEmpty();
        }
    }

    /**
     * A captured structure.
     *
     * @param template the structure template
     * @param origin   the world position the template was captured from (its minimum corner)
     */
    public record Captured(StructureTemplate template, BlockPos origin) {
    }

    /**
     * Captures the built content of an area.
     *
     * @param level the level
     * @param area  the area to capture
     * @return the capture, or empty if nothing but air and platform blocks is inside the area
     */
    public static Optional<Captured> capture(ServerLevel level, BoundingBox area) {
        Optional<BoundingBox> built = builtBounds(level, area);
        if (built.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox box = withDecorations(level, area, built.get());
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), true,
                List.of(ModBlocks.EDITOR_PLATFORM.get()));
        return Optional.of(new Captured(withoutMobs(level, template), origin));
    }

    /**
     * Checks whether an area contains nothing but air, platform blocks and players.
     *
     * @param level the level
     * @param area  the area
     * @return {@code true} if nothing was built in the area
     */
    public static boolean isEmpty(ServerLevel level, BoundingBox area) {
        return builtBounds(level, area).isEmpty()
                && level.getEntities((Entity) null, AABB.of(area), entity -> !(entity instanceof Player)).isEmpty();
    }

    /**
     * Validates a captured structure: at least one player spawn marker and one exit marker are required; spawn
     * markers without two free blocks above them and mob markers without a spawn egg (both checked in the world
     * it was captured from) only produce a warning.
     *
     * @param level    the level the structure was captured from
     * @param captured the capture
     * @return the validation result
     */
    public static Validation validate(ServerLevel level, Captured captured) {
        return validate(level, captured, false);
    }

    /**
     * Validates a captured sub structure (US-32): Player Spawn and Exit Markers are optional, Sub Structure
     * Markers are not allowed (no nesting); the warnings are the same as for challenge structures.
     *
     * @param level    the level the structure was captured from
     * @param captured the capture
     * @return the validation result
     */
    public static Validation validateSub(ServerLevel level, Captured captured) {
        return validate(level, captured, true);
    }

    private static Validation validate(ServerLevel level, Captured captured, boolean sub) {
        StructureTemplate template = captured.template();
        List<Component> errors = new ArrayList<>();
        List<Component> warnings = new ArrayList<>();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<StructureTemplate.StructureBlockInfo> spawns = template.filterBlocks(BlockPos.ZERO, settings, ModBlocks.PLAYER_SPAWN_MARKER.get());
        if (sub) {
            if (!template.filterBlocks(BlockPos.ZERO, settings, ModBlocks.SUB_STRUCTURE_MARKER.get()).isEmpty()) {
                errors.add(Component.translatable("message.architectstrials.save.nested_sub_structure"));
            }
        } else {
            if (spawns.isEmpty()) {
                errors.add(Component.translatable("message.architectstrials.save.no_spawn_marker"));
            }
            if (template.filterBlocks(BlockPos.ZERO, settings, ModBlocks.EXIT_MARKER.get()).isEmpty()) {
                errors.add(Component.translatable("message.architectstrials.save.no_exit_marker"));
            }
        }
        for (StructureTemplate.StructureBlockInfo spawn : spawns) {
            BlockPos marker = captured.origin().offset(spawn.pos());
            if (!isFree(level, marker.above()) || !isFree(level, marker.above(2))) {
                warnings.add(Component.translatable("message.architectstrials.save.spawn_marker_blocked",
                        spawn.pos().getX(), spawn.pos().getY(), spawn.pos().getZ()));
            }
        }
        for (Block mobMarker : List.of(ModBlocks.DIRECT_SPAWN_MARKER.get(), ModBlocks.SPAWNER_MARKER.get(), ModBlocks.TRIAL_SPAWNER_MARKER.get())) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, mobMarker)) {
                if (!(level.getBlockEntity(captured.origin().offset(info.pos())) instanceof MobMarkerBlockEntity marker)
                        || !marker.hasAnyEgg()) {
                    warnings.add(Component.translatable("message.architectstrials.save.mob_marker_empty", mobMarker.getName(),
                            info.pos().getX(), info.pos().getY(), info.pos().getZ()));
                }
            }
        }
        return new Validation(errors, warnings);
    }

    /**
     * Places a template into the editor: centered on the origin, bottom at the structure placement height — the
     * same way it will later be placed in a challenge.
     *
     * @param level    the editor level
     * @param template the template
     * @return the box the template was placed into
     */
    public static BoundingBox place(ServerLevel level, StructureTemplate template) {
        return place(level, template, 0, 0);
    }

    /**
     * Places a template centered on a horizontal position, bottom at the structure placement height.
     *
     * @param level    the level
     * @param template the template
     * @param centerX  the x coordinate to center on
     * @param centerZ  the z coordinate to center on
     * @return the box the template was placed into
     */
    public static BoundingBox place(ServerLevel level, StructureTemplate template, int centerX, int centerZ) {
        Vec3i size = template.getSize();
        BlockPos origin = new BlockPos(centerX - size.getX() / 2, ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt(), centerZ - size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        PaintingPlacement.place(level, template, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return template.getBoundingBox(settings, origin);
    }

    /**
     * Copies a box of any level 1:1 into a template — blocks, block entities and decoration entities, no mobs —
     * without trimming (used for importing world areas).
     *
     * @param level the level
     * @param box   the box
     * @return the template
     */
    public static StructureTemplate copy(ServerLevel level, BoundingBox box) {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(box.minX(), box.minY(), box.minZ()),
                new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()), true, List.of());
        return withoutMobs(level, template);
    }

    /**
     * Counts the non-air blocks of a box, skipping empty chunk sections.
     *
     * @param level the level
     * @param box   the box
     * @return the number of non-air blocks
     */
    public static int countBlocks(ServerLevel level, BoundingBox box) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX()); chunkX <= SectionPos.blockToSectionCoord(box.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ()); chunkZ <= SectionPos.blockToSectionCoord(box.maxZ()); chunkZ++) {
                LevelChunkSection[] sections = level.getChunk(chunkX, chunkZ).getSections();
                for (int index = 0; index < sections.length; index++) {
                    if (sections[index].hasOnlyAir()) {
                        continue;
                    }
                    int sectionMinY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));
                    int fromY = Math.max(box.minY(), sectionMinY);
                    int toY = Math.min(box.maxY(), sectionMinY + SectionPos.SECTION_SIZE - 1);
                    int fromX = Math.max(box.minX(), SectionPos.sectionToBlockCoord(chunkX));
                    int toX = Math.min(box.maxX(), SectionPos.sectionToBlockCoord(chunkX, 15));
                    int fromZ = Math.max(box.minZ(), SectionPos.sectionToBlockCoord(chunkZ));
                    int toZ = Math.min(box.maxZ(), SectionPos.sectionToBlockCoord(chunkZ, 15));
                    for (int y = fromY; y <= toY; y++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int x = fromX; x <= toX; x++) {
                                if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Finds the smallest box containing every block in the area that is neither air nor an Editor Platform block.
     */
    private static Optional<BoundingBox> builtBounds(ServerLevel level, BoundingBox area) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = SectionPos.blockToSectionCoord(area.minX()); chunkX <= SectionPos.blockToSectionCoord(area.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(area.minZ()); chunkZ <= SectionPos.blockToSectionCoord(area.maxZ()); chunkZ++) {
                LevelChunkSection[] sections = level.getChunk(chunkX, chunkZ).getSections();
                for (int index = 0; index < sections.length; index++) {
                    if (sections[index].hasOnlyAir()) {
                        continue;
                    }
                    int sectionMinY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));
                    int fromY = Math.max(area.minY(), sectionMinY);
                    int toY = Math.min(area.maxY(), sectionMinY + SectionPos.SECTION_SIZE - 1);
                    int fromX = Math.max(area.minX(), SectionPos.sectionToBlockCoord(chunkX));
                    int toX = Math.min(area.maxX(), SectionPos.sectionToBlockCoord(chunkX, 15));
                    int fromZ = Math.max(area.minZ(), SectionPos.sectionToBlockCoord(chunkZ));
                    int toZ = Math.min(area.maxZ(), SectionPos.sectionToBlockCoord(chunkZ, 15));
                    for (int y = fromY; y <= toY; y++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int x = fromX; x <= toX; x++) {
                                BlockState state = level.getBlockState(pos.set(x, y, z));
                                if (!state.isAir() && !state.is(ModBlocks.EDITOR_PLATFORM.get())) {
                                    minX = Math.min(minX, x);
                                    minY = Math.min(minY, y);
                                    minZ = Math.min(minZ, z);
                                    maxX = Math.max(maxX, x);
                                    maxY = Math.max(maxY, y);
                                    maxZ = Math.max(maxZ, z);
                                }
                            }
                        }
                    }
                }
            }
        }
        return minX == Integer.MAX_VALUE ? Optional.empty() : Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Grows the built block bounds so they also contain every kept entity (item frames, paintings, armor stands,
     * …) inside the area: an item frame hanging on the outermost wall occupies an otherwise empty block and would
     * be cut off otherwise.
     */
    private static BoundingBox withDecorations(ServerLevel level, BoundingBox area, BoundingBox blocks) {
        int minX = blocks.minX();
        int minY = blocks.minY();
        int minZ = blocks.minZ();
        int maxX = blocks.maxX();
        int maxY = blocks.maxY();
        int maxZ = blocks.maxZ();
        for (Entity entity : level.getEntities((Entity) null, AABB.of(area),
                entity -> !(entity instanceof Player) && entity.getType().getCategory() == MobCategory.MISC)) {
            BlockPos pos = entity.blockPosition();
            if (area.isInside(pos)) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Removes mobs from a captured template by filtering its serialized entity list: only entities of the
     * {@link MobCategory#MISC} category (item frames, armor stands, paintings, …) are kept.
     */
    private static StructureTemplate withoutMobs(ServerLevel level, StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag kept = new ListTag();
        for (Tag entry : tag.getListOrEmpty(StructureTemplate.ENTITIES_TAG)) {
            if (entry instanceof CompoundTag entity && isNotMob(entity.getCompoundOrEmpty(StructureTemplate.ENTITY_TAG_NBT))) {
                kept.add(entity);
            }
        }
        tag.put(StructureTemplate.ENTITIES_TAG, kept);
        StructureTemplate filtered = new StructureTemplate();
        filtered.load(level.holderLookup(Registries.BLOCK), tag);
        return filtered;
    }

    private static boolean isNotMob(CompoundTag entityData) {
        Identifier id = Identifier.tryParse(entityData.getStringOr("id", ""));
        return id == null || BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getCategory() == MobCategory.MISC).orElse(true);
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}

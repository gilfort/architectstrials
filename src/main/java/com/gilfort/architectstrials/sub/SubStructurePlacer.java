package com.gilfort.architectstrials.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.slot.SlotManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Places the sub structures of a freshly placed challenge structure (US-32), before its markers are resolved.
 * <p>
 * For every Sub Structure Marker of the structure: the marker is removed, its setup is rolled and the rolled sub
 * structure is placed with the marker's facing as rotation, at the marker or at a random block of the marker's
 * offset area (the area turns and mirrors with the parent structure). A generation <em>fails</em> if the sub
 * structure would overwrite a Player Spawn Marker or Exit Marker, would reach beyond the slot area or does not
 * exist; then the setup is rolled once more, and a second failure places nothing. Sub Structure Markers inside
 * sub structures are removed without placing anything (no nesting). Other markers of the sub structures are
 * resolved afterwards with the rest, through {@link #placed}.
 */
public final class SubStructurePlacer {

    private static final int ATTEMPTS = 2;

    private final ServerLevel level;
    private final BoundingBox slotArea;
    private final RandomSource random;
    private final List<Placed> placed = new ArrayList<>();

    /**
     * A placed sub structure, whose markers still have to be resolved.
     *
     * @param template the sub structure's template
     * @param origin   the placement origin
     * @param settings the placement settings
     */
    public record Placed(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
    }

    /**
     * Creates a placer for one instance.
     *
     * @param level  the theme level
     * @param slot   the instance's slot index
     * @param random the placement random source
     */
    public SubStructurePlacer(ServerLevel level, int slot, RandomSource random) {
        this.level = level;
        this.slotArea = SlotManager.slot(level, slot).area(level.getMinY(), level.getMaxY());
        this.random = random;
    }

    /** @return the sub structures placed so far */
    public List<Placed> placed() {
        return List.copyOf(this.placed);
    }

    /**
     * Places the sub structures of all Sub Structure Markers of a placed structure.
     *
     * @param template the placed parent template
     * @param origin   the parent's placement origin
     * @param settings the parent's placement settings
     * @return the number of placed sub structures
     */
    public int placeAll(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        int count = 0;
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.SUB_STRUCTURE_MARKER.get())) {
            if (this.placeAt(info.pos(), settings.getRotation(), settings.getMirror())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Rolls and places the sub structure of one marker and removes the marker.
     *
     * @param pos      the marker position in the world
     * @param rotation the rotation of the parent structure (for the offset area)
     * @param mirror   the mirroring of the parent structure (for the offset area)
     * @return {@code true} if a sub structure was placed
     */
    public boolean placeAt(BlockPos pos, Rotation rotation, Mirror mirror) {
        BlockState marker = this.level.getBlockState(pos);
        if (!marker.is(ModBlocks.SUB_STRUCTURE_MARKER.get())
                || !(this.level.getBlockEntity(pos) instanceof SubStructureMarkerBlockEntity entity)) {
            return false;
        }
        SubStructureSetup setup = entity.setup();
        int[] offsets = entity.offsets();
        Direction facing = marker.getValue(HorizontalDirectionalBlock.FACING);
        this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Optional<Identifier> rolled = setup.roll(this.random);
            if (rolled.isEmpty()) {
                return false;
            }
            String failure = this.tryPlace(rolled.get(), pos, facing, offsets, rotation, mirror);
            if (failure == null) {
                return true;
            }
            if (ArchitectsTrialsConfig.LOG_FAILED_SUB_STRUCTURES.getAsBoolean()) {
                ArchitectsTrials.LOGGER.info("Sub structure {} at {} failed (attempt {} of {}): {}", rolled.get(), pos.toShortString(),
                        attempt, ATTEMPTS, failure);
            }
        }
        return false;
    }

    /**
     * Tries to place one sub structure.
     *
     * @return {@code null} on success, otherwise the reason of the failure
     */
    private @Nullable String tryPlace(Identifier id, BlockPos markerPos, Direction facing, int[] offsets,
            Rotation parentRotation, Mirror parentMirror) {
        if (SubStructures.get(id).isEmpty()) {
            return "unknown sub structure";
        }
        Optional<StructureTemplate> template = this.level.getServer().getStructureTemplateManager().get(SubStructures.get(id).get().structure());
        if (template.isEmpty()) {
            return "structure template " + SubStructures.get(id).get().structure() + " is missing";
        }
        BlockPos local = new BlockPos(this.between(offsets[0], offsets[1]), this.between(offsets[2], offsets[3]), this.between(offsets[4], offsets[5]));
        BlockPos origin = markerPos.offset(StructureTemplate.transform(local, parentMirror, parentRotation, BlockPos.ZERO));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(SubStructureMarkerBlock.rotation(facing));
        BoundingBox box = template.get().getBoundingBox(settings, origin);
        if (!this.slotArea.isInside(new BlockPos(box.minX(), box.minY(), box.minZ()))
                || !this.slotArea.isInside(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            return "it would reach beyond the slot area";
        }
        List<StructureTemplate.StructureBlockInfo> blocks = template.get().palettes.isEmpty() ? List.of()
                : settings.getRandomPalette(template.get().palettes, origin).blocks();
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockState existing = this.level.getBlockState(StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin));
            if (existing.is(ModBlocks.PLAYER_SPAWN_MARKER.get()) || existing.is(ModBlocks.EXIT_MARKER.get())) {
                return "it would overwrite a Player Spawn Marker or Exit Marker";
            }
        }
        template.get().placeInWorld(this.level, origin, origin, settings, this.random, Block.UPDATE_CLIENTS);
        // No nesting: Sub Structure Markers inside sub structures place nothing.
        for (StructureTemplate.StructureBlockInfo nested : template.get().filterBlocks(origin, settings, ModBlocks.SUB_STRUCTURE_MARKER.get())) {
            this.level.setBlock(nested.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        this.placed.add(new Placed(template.get(), origin, settings));
        return null;
    }

    /** @return a random value in {@code -negative..positive} */
    private int between(int negative, int positive) {
        return negative + positive == 0 ? 0 : this.random.nextInt(negative + positive + 1) - negative;
    }
}

package com.gilfort.architectstrials.slot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Removes all blocks, block entities and non-player entities from a released slot, spread over many ticks.
 * <p>
 * Works chunk section by chunk section and skips sections that contain only air, so empty space costs
 * almost nothing. Blocks are removed without drops, neighbour updates or block entity side effects.
 */
final class SlotClearTask {

    /** Flags for silent removal: sync to clients, but no drops, neighbour updates or container spills. */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private final ResourceKey<Level> dimension;
    private final int index;
    private final BoundingBox area;
    private final int minChunkX;
    private final int maxChunkX;
    private final int maxChunkZ;
    private int chunkX;
    private int chunkZ;
    private int sectionIndex;
    private boolean done;

    /**
     * Creates a clear task for a slot.
     *
     * @param level the level containing the slot
     * @param slot  the slot to clear
     */
    SlotClearTask(ServerLevel level, Slot slot) {
        this.dimension = level.dimension();
        this.index = slot.index();
        this.area = slot.area(level.getMinY(), level.getMaxY());
        this.minChunkX = SectionPos.blockToSectionCoord(this.area.minX());
        this.maxChunkX = SectionPos.blockToSectionCoord(this.area.maxX());
        this.maxChunkZ = SectionPos.blockToSectionCoord(this.area.maxZ());
        this.chunkX = this.minChunkX;
        this.chunkZ = SectionPos.blockToSectionCoord(this.area.minZ());
    }

    /** @return the dimension of the slot */
    ResourceKey<Level> dimension() {
        return this.dimension;
    }

    /** @return the slot index */
    int index() {
        return this.index;
    }

    /** @return {@code true} once all content has been removed */
    boolean isDone() {
        return this.done;
    }

    /**
     * Clears up to {@code sectionBudget} non-empty chunk sections. Removes the remaining entities once all
     * sections are processed.
     *
     * @param level         the level containing the slot
     * @param sectionBudget the maximum number of non-empty sections to clear in this step
     */
    void step(ServerLevel level, int sectionBudget) {
        int budget = sectionBudget;
        while (budget > 0 && !this.done) {
            LevelChunk chunk = level.getChunk(this.chunkX, this.chunkZ);
            LevelChunkSection[] sections = chunk.getSections();
            if (this.sectionIndex < sections.length) {
                if (!sections[this.sectionIndex].hasOnlyAir()) {
                    this.clearSection(level, level.getSectionYFromSectionIndex(this.sectionIndex));
                    budget--;
                }
                this.sectionIndex++;
            } else {
                this.advanceChunk(level);
            }
        }
    }

    private void advanceChunk(ServerLevel level) {
        this.sectionIndex = 0;
        if (this.chunkX < this.maxChunkX) {
            this.chunkX++;
            return;
        }
        this.chunkX = this.minChunkX;
        if (this.chunkZ < this.maxChunkZ) {
            this.chunkZ++;
            return;
        }
        this.removeEntities(level);
        this.done = true;
    }

    private void clearSection(ServerLevel level, int sectionY) {
        int minX = Math.max(this.area.minX(), SectionPos.sectionToBlockCoord(this.chunkX));
        int maxX = Math.min(this.area.maxX(), SectionPos.sectionToBlockCoord(this.chunkX, 15));
        int minZ = Math.max(this.area.minZ(), SectionPos.sectionToBlockCoord(this.chunkZ));
        int maxZ = Math.min(this.area.maxZ(), SectionPos.sectionToBlockCoord(this.chunkZ, 15));
        int minY = SectionPos.sectionToBlockCoord(sectionY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = minY; y < minY + SectionPos.SECTION_SIZE; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, CLEAR_FLAGS);
                    }
                }
            }
        }
    }

    private void removeEntities(ServerLevel level) {
        for (Entity entity : level.getEntities((Entity) null, AABB.of(this.area), entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }
}

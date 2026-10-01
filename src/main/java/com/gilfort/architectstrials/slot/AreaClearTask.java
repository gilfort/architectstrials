package com.gilfort.architectstrials.slot;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModTicketTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
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
 * Removes all blocks, block entities and non-player entities from an area (a released slot, the editor
 * dimension), spread over many ticks.
 * <p>
 * Works chunk section by chunk section and skips sections that contain only air, so empty space costs
 * almost nothing. Blocks are removed without drops, neighbour updates or block entity side effects.
 * <p>
 * Entities are stored separately from blocks and are only loaded while their chunk is loaded at full status.
 * A slot is usually cleared after all players left it, so its entities (armor stands, item frames, persistent
 * mobs, …) would still sit on disk and reappear in the next instance of that slot. The task therefore keeps
 * all chunks of the area loaded with an {@link ModTicketTypes#AREA_CLEAR} ticket and removes the entities only
 * once every chunk's entities are loaded (or after {@value #MAX_ENTITY_WAIT_TICKS} ticks at the latest).
 */
final class AreaClearTask {

    /** Flags for silent removal: sync to clients, but no drops, neighbour updates or container spills. */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    /** Upper bound for waiting on entity loading, in ticks. */
    private static final int MAX_ENTITY_WAIT_TICKS = 600;

    private final ResourceKey<Level> dimension;
    private final Consumer<ServerLevel> onDone;
    private final BoundingBox area;
    private final int minChunkX;
    private final int maxChunkX;
    private final int maxChunkZ;
    private int chunkX;
    private int chunkZ;
    private int sectionIndex;
    private boolean ticketsAdded;
    private boolean blocksCleared;
    private int entityWaitTicks;
    private boolean done;

    /**
     * Creates a clear task.
     *
     * @param level  the level containing the area
     * @param area   the area to clear
     * @param onDone called with the level once the area is empty
     */
    AreaClearTask(ServerLevel level, BoundingBox area, Consumer<ServerLevel> onDone) {
        this.dimension = level.dimension();
        this.onDone = onDone;
        this.area = area;
        this.minChunkX = SectionPos.blockToSectionCoord(this.area.minX());
        this.maxChunkX = SectionPos.blockToSectionCoord(this.area.maxX());
        this.maxChunkZ = SectionPos.blockToSectionCoord(this.area.maxZ());
        this.chunkX = this.minChunkX;
        this.chunkZ = SectionPos.blockToSectionCoord(this.area.minZ());
    }

    /** @return the dimension of the area */
    ResourceKey<Level> dimension() {
        return this.dimension;
    }

    /** @return the callback run once the area is empty */
    Consumer<ServerLevel> onDone() {
        return this.onDone;
    }

    /** @return {@code true} once all content has been removed */
    boolean isDone() {
        return this.done;
    }

    /**
     * Clears up to {@code sectionBudget} non-empty chunk sections. Removes the remaining entities once all
     * sections are processed.
     *
     * @param level         the level containing the area
     * @param sectionBudget the maximum number of non-empty sections to clear in this step
     */
    void step(ServerLevel level, int sectionBudget) {
        if (!this.ticketsAdded) {
            this.forEachChunk(pos -> level.getChunkSource().addTicketWithRadius(ModTicketTypes.AREA_CLEAR.get(), pos, 0));
            this.ticketsAdded = true;
        }
        if (this.blocksCleared) {
            this.finishWhenEntitiesLoaded(level);
            return;
        }
        int budget = sectionBudget;
        while (budget > 0 && !this.blocksCleared) {
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
        this.blocksCleared = true;
    }

    /**
     * Removes the area's entities once all its chunks have loaded their entities, then releases the tickets.
     */
    private void finishWhenEntitiesLoaded(ServerLevel level) {
        boolean[] loaded = {true};
        this.forEachChunk(pos -> loaded[0] &= level.areEntitiesLoaded(pos.pack()));
        if (!loaded[0] && ++this.entityWaitTicks < MAX_ENTITY_WAIT_TICKS) {
            return;
        }
        if (!loaded[0]) {
            ArchitectsTrials.LOGGER.warn("Entities of area {} in {} did not finish loading; removing the loaded ones only", this.area,
                    this.dimension.identifier());
        }
        this.removeEntities(level);
        this.forEachChunk(pos -> level.getChunkSource().removeTicketWithRadius(ModTicketTypes.AREA_CLEAR.get(), pos, 0));
        this.done = true;
    }

    private void forEachChunk(Consumer<ChunkPos> action) {
        for (int x = this.minChunkX; x <= this.maxChunkX; x++) {
            for (int z = SectionPos.blockToSectionCoord(this.area.minZ()); z <= this.maxChunkZ; z++) {
                action.accept(new ChunkPos(x, z));
            }
        }
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

package com.gilfort.architectstrials.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.registry.ModTicketTypes;
import com.gilfort.architectstrials.structure.OreGeneration;
import com.gilfort.architectstrials.structure.PaintingPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Places the structure of one new instance over several ticks (US-28).
 * <p>
 * Steps, each limited by a fixed per-tick budget:
 * <ol>
 * <li>keep all chunks of the structure loaded with an {@link ModTicketTypes#INSTANCE_PLACEMENT} ticket and wait
 * until they are loaded (no synchronous chunk loading on the server thread)</li>
 * <li>sort the template's blocks and entities by the chunk section they end up in</li>
 * <li>place the sections bottom-up; every section is a sub-template with the same size, placed by vanilla's own
 * {@link StructureTemplate#placeInWorld} with the instance's settings, so the result is identical to placing the
 * whole template at once</li>
 * <li>if the challenge has natural ore generation (US-37), place the ores of its biome into the structure,
 * a few chunk columns per tick (see {@link OreGenerator})</li>
 * <li>add paintings through {@link PaintingPlacement}, resolve all markers, fill the exits' portal space with
 * {@link ChallengeExitPortalBlock}s, seal the exits that require the instance's required mobs (US-30) and make
 * the instance ready</li>
 * </ol>
 * The instance exists from the start but has no spawn points, so portals stay in their forming state until the
 * task is done. If the instance disappears meanwhile (time limit, command), the task stops.
 */
final class PlacementTask {

    /** Maximum number of template blocks placed per tick. */
    static final int BLOCKS_PER_TICK = 16_384;

    /** Maximum number of template blocks sorted into sections per tick. */
    private static final int INDEX_PER_TICK = 131_072;

    /** Ticks to wait for chunks to load before loading the rest synchronously. */
    private static final int MAX_CHUNK_WAIT_TICKS = 600;

    /** Maximum number of chunk columns that get their ores per tick (US-37). */
    private static final int ORE_COLUMNS_PER_TICK = 2;

    private enum Step { LOAD_CHUNKS, INDEX, PLACE, ORES, FINISH, DONE }

    private final ResourceKey<Level> dimension;
    private final UUID instanceId;
    private final StructureTemplate template;
    private final BlockPos origin;
    private final StructurePlaceSettings settings;
    private final RandomSource random;
    private final List<int[]> chunks = new ArrayList<>();
    private final List<StructureTemplate.StructureBlockInfo> blocks;
    private final Map<Long, List<StructureTemplate.StructureBlockInfo>> blocksBySection = new HashMap<>();
    private final Map<Long, List<StructureTemplate.StructureEntityInfo>> entitiesBySection = new HashMap<>();
    private final List<StructureTemplate.StructureEntityInfo> paintings = new ArrayList<>();
    private final List<Consumer<ChallengeInstance>> callbacks = new ArrayList<>();
    private final Optional<OreGeneration> ores;
    private @Nullable OreGenerator oreGenerator;
    private List<int[]> oreColumns = List.of();
    private List<Long> sectionOrder = List.of();
    private Step step = Step.LOAD_CHUNKS;
    private boolean ticketsAdded;
    private int chunkWaitTicks;
    private int cursor;

    /**
     * Creates a placement task.
     *
     * @param level      the theme level
     * @param instanceId the id of the (not yet ready) instance
     * @param template   the template to place
     * @param origin     the placement origin
     * @param settings   the placement settings (rotation, mirroring)
     * @param random     the random source of this placement
     * @param ores       the challenge's natural ore generation (US-37), if any
     */
    PlacementTask(ServerLevel level, UUID instanceId, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            RandomSource random, Optional<OreGeneration> ores) {
        this.dimension = level.dimension();
        this.instanceId = instanceId;
        this.template = template;
        this.origin = origin;
        this.settings = settings;
        this.random = random;
        this.ores = ores;
        this.blocks = template.palettes.isEmpty() ? List.of() : settings.getRandomPalette(template.palettes, origin).blocks();
        BoundingBox box = template.getBoundingBox(settings, origin);
        // Ore blobs near the structure's edge may reach into the neighbouring chunks; keep those loaded as well.
        int margin = ores.isPresent() ? 1 : 0;
        for (int x = SectionPos.blockToSectionCoord(box.minX()) - margin; x <= SectionPos.blockToSectionCoord(box.maxX()) + margin; x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()) - margin; z <= SectionPos.blockToSectionCoord(box.maxZ()) + margin; z++) {
                this.chunks.add(new int[] {x, z});
            }
        }
    }

    /** @return the dimension of the instance */
    ResourceKey<Level> dimension() {
        return this.dimension;
    }

    /** @return the id of the instance being placed */
    UUID instanceId() {
        return this.instanceId;
    }

    /** @return {@code true} once the task has finished or stopped */
    boolean isDone() {
        return this.step == Step.DONE;
    }

    /**
     * Registers a callback run with the ready instance once placement is done. Not called if the placement stops
     * because the instance was removed.
     *
     * @param callback the callback
     */
    void whenReady(Consumer<ChallengeInstance> callback) {
        this.callbacks.add(callback);
    }

    /**
     * Advances the task by one tick's budget.
     *
     * @param level the theme level
     */
    void step(ServerLevel level) {
        this.advance(level, false);
    }

    /**
     * Runs the whole task now, loading chunks synchronously (GameTests).
     *
     * @param level the theme level
     */
    void runToCompletion(ServerLevel level) {
        while (!this.isDone()) {
            this.advance(level, true);
        }
    }

    /**
     * Stops the task and releases its chunk tickets.
     *
     * @param level the theme level
     */
    void cancel(ServerLevel level) {
        this.releaseTickets(level);
        this.step = Step.DONE;
    }

    private void advance(ServerLevel level, boolean unlimited) {
        if (this.step != Step.DONE && InstanceManager.data(level).get(this.instanceId).isEmpty()) {
            ArchitectsTrials.LOGGER.debug("Instance {} was removed during placement; placement stopped", this.instanceId);
            this.cancel(level);
            return;
        }
        switch (this.step) {
            case LOAD_CHUNKS -> this.loadChunks(level, unlimited);
            case INDEX -> this.index(unlimited);
            case PLACE -> this.place(level, unlimited);
            case ORES -> this.generateOres(level, unlimited);
            case FINISH -> this.finish(level);
            case DONE -> {
            }
        }
    }

    private void loadChunks(ServerLevel level, boolean unlimited) {
        if (unlimited) {
            // Immediate placement loads the chunks synchronously below, like vanilla's placeInWorld does, and
            // leaves their lifetime to vanilla; a ticket released right afterwards would only hide them early.
            this.chunks.forEach(chunk -> level.getChunk(chunk[0], chunk[1]));
            this.step = Step.INDEX;
            return;
        }
        if (!this.ticketsAdded) {
            this.chunks.forEach(chunk -> level.getChunkSource().addTicketWithRadius(ModTicketTypes.INSTANCE_PLACEMENT.get(),
                    new ChunkPos(chunk[0], chunk[1]), 0));
            this.ticketsAdded = true;
        }
        boolean force = ++this.chunkWaitTicks > MAX_CHUNK_WAIT_TICKS;
        for (int[] chunk : this.chunks) {
            if (level.getChunkSource().getChunkNow(chunk[0], chunk[1]) == null) {
                if (!force) {
                    return;
                }
                level.getChunk(chunk[0], chunk[1]);
            }
        }
        this.step = Step.INDEX;
    }

    private void index(boolean unlimited) {
        int end = unlimited ? this.blocks.size() : Math.min(this.blocks.size(), this.cursor + INDEX_PER_TICK);
        for (; this.cursor < end; this.cursor++) {
            StructureTemplate.StructureBlockInfo info = this.blocks.get(this.cursor);
            this.blocksBySection.computeIfAbsent(this.sectionOf(info.pos()), key -> new ArrayList<>()).add(info);
        }
        if (this.cursor < this.blocks.size()) {
            return;
        }
        if (!this.settings.isIgnoreEntities()) {
            for (StructureTemplate.StructureEntityInfo entity : this.template.entityInfoList) {
                if (PaintingPlacement.isPainting(entity)) {
                    this.paintings.add(entity);
                } else {
                    this.entitiesBySection.computeIfAbsent(this.sectionOf(entity.blockPos), key -> new ArrayList<>()).add(entity);
                }
            }
        }
        List<Long> order = new ArrayList<>(this.blocksBySection.keySet());
        this.entitiesBySection.keySet().stream().filter(key -> !this.blocksBySection.containsKey(key)).forEach(order::add);
        order.sort((a, b) -> {
            int byY = Integer.compare(SectionPos.y(a), SectionPos.y(b));
            if (byY != 0) {
                return byY;
            }
            int byX = Integer.compare(SectionPos.x(a), SectionPos.x(b));
            return byX != 0 ? byX : Integer.compare(SectionPos.z(a), SectionPos.z(b));
        });
        this.sectionOrder = order;
        this.cursor = 0;
        this.step = Step.PLACE;
    }

    private long sectionOf(BlockPos localPos) {
        BlockPos pos = StructureTemplate.calculateRelativePosition(this.settings, localPos).offset(this.origin);
        return SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private void place(ServerLevel level, boolean unlimited) {
        int budget = BLOCKS_PER_TICK;
        while (this.cursor < this.sectionOrder.size() && (unlimited || budget > 0)) {
            long key = this.sectionOrder.get(this.cursor++);
            List<StructureTemplate.StructureBlockInfo> sectionBlocks = this.blocksBySection.getOrDefault(key, List.of());
            StructureTemplate section = new StructureTemplate();
            section.palettes.add(new StructureTemplate.Palette(sectionBlocks));
            section.entityInfoList.addAll(this.entitiesBySection.getOrDefault(key, List.of()));
            section.size = this.template.getSize();
            section.placeInWorld(level, this.origin, this.origin, this.settings, this.random, Block.UPDATE_CLIENTS);
            budget -= Math.max(1, sectionBlocks.size());
        }
        if (this.cursor >= this.sectionOrder.size()) {
            this.blocksBySection.clear();
            this.entitiesBySection.clear();
            this.cursor = 0;
            this.step = this.ores.isPresent() ? Step.ORES : Step.FINISH;
        }
    }

    private void generateOres(ServerLevel level, boolean unlimited) {
        if (this.oreGenerator == null) {
            BoundingBox box = this.template.getBoundingBox(this.settings, this.origin);
            this.oreGenerator = OreGenerator.create(level, this.ores.orElseThrow(), box, this.random.nextLong());
            if (this.oreGenerator == null) {
                this.step = Step.FINISH;
                return;
            }
            this.oreColumns = this.oreGenerator.columns();
        }
        int budget = ORE_COLUMNS_PER_TICK;
        while (this.cursor < this.oreColumns.size() && (unlimited || budget-- > 0)) {
            int[] column = this.oreColumns.get(this.cursor++);
            this.oreGenerator.generate(level, column[0], column[1]);
        }
        if (this.cursor >= this.oreColumns.size()) {
            // Ore features write into the chunk sections directly, like world generation does.
            this.chunks.forEach(chunk -> level.getChunk(chunk[0], chunk[1]).markUnsaved());
            this.oreGenerator = null;
            this.step = Step.FINISH;
        }
    }

    private void finish(ServerLevel level) {
        this.paintings.forEach(painting -> PaintingPlacement.addPainting(level, painting, this.origin, this.settings));
        ChallengeInstance placed = InstanceManager.data(level).get(this.instanceId).orElseThrow();
        MarkerContext context = new MarkerContext(level, placed, this.random);
        int markers = MarkerResolvers.resolveAll(context, this.template, this.origin, this.settings);
        ChallengeExitPortalBlock.fill(level, context.exits());
        RequiredMobs required = context.requiredMobs().isEmpty() ? RequiredMobs.NONE : RequiredMobs.of(context.requiredMobs());
        if (required.any()) {
            context.exits().forEach(exit -> ChallengeExitBlock.sealIfRequiringMobs(level, exit));
        }
        ChallengeInstance instance = placed.withSpawnPoints(context.spawnPoints()).withExits(context.exits()).withRequiredMobs(required);
        this.releaseTickets(level);
        this.step = Step.DONE;
        if (!instance.ready()) {
            ArchitectsTrials.LOGGER.warn("Instance {} of structure {} has no player spawn markers and is discarded", instance.id(),
                    instance.structure());
            InstanceManager.close(level, instance.id());
            return;
        }
        InstanceManager.update(level, instance);
        ArchitectsTrials.LOGGER.debug("Placed instance {} of {} tier {} with structure {} in slot {} ({} markers)",
                instance.id(), instance.theme(), instance.tier(), instance.structure(), instance.slot(), markers);
        this.callbacks.forEach(callback -> callback.accept(instance));
    }

    private void releaseTickets(ServerLevel level) {
        if (this.ticketsAdded) {
            this.chunks.forEach(chunk -> level.getChunkSource().removeTicketWithRadius(ModTicketTypes.INSTANCE_PLACEMENT.get(),
                    new ChunkPos(chunk[0], chunk[1]), 0));
            this.ticketsAdded = false;
        }
    }
}

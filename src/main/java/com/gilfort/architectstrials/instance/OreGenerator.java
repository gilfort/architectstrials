package com.gilfort.architectstrials.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.structure.OreGeneration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Places natural ores into a freshly placed structure (US-37), one chunk column at a time.
 * <p>
 * Uses the placed features of the configured biome's {@link GenerationStep.Decoration#UNDERGROUND_ORES} step and
 * the ore features of its {@link GenerationStep.Decoration#UNDERGROUND_DECORATION} step (Nether ores), including
 * ores other mods add through biome modifiers. Their placement modifiers run as in vanilla world
 * generation, except the biome filter (the challenge dimension's own biome would reject them), against a
 * simulated height range; the resulting heights are stretched linearly onto the structure's layers. The features
 * then replace only what their own rules allow (stone, deepslate, netherrack, …), so markers and built decoration
 * stay untouched. Positions outside the structure are dropped. Each instance uses its own seed.
 */
final class OreGenerator {

    private static final int ORE_STEP = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
    private static final int DECORATION_STEP = GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal();
    private static final int[] STEPS = {ORE_STEP, DECORATION_STEP};

    private final List<PlacedFeature> features;
    private final List<Integer> featureSteps;
    private final BoundingBox box;
    private final int simulatedMin;
    private final int simulatedHeight;
    private final float density;
    private final long seed;

    private OreGenerator(List<PlacedFeature> features, List<Integer> featureSteps, BoundingBox box, int simulatedMin, int simulatedHeight,
            float density, long seed) {
        this.features = features;
        this.featureSteps = featureSteps;
        this.box = box;
        this.simulatedMin = simulatedMin;
        this.simulatedHeight = simulatedHeight;
        this.density = density;
        this.seed = seed;
    }

    /**
     * Prepares ore generation for a structure.
     *
     * @param level    the theme level
     * @param settings the challenge's ore generation settings
     * @param box      the placed structure's bounding box
     * @param seed     the instance's ore seed
     * @return the generator, or {@code null} if the biome is unknown (logged) or has no ore features
     */
    static @Nullable OreGenerator create(ServerLevel level, OreGeneration settings, BoundingBox box, long seed) {
        Optional<? extends Holder<Biome>> biome = level.registryAccess().lookupOrThrow(Registries.BIOME).get(settings.biome());
        if (biome.isEmpty()) {
            ArchitectsTrials.LOGGER.warn("Ore generation uses unknown biome {}; no ores are placed", settings.biome().identifier());
            return null;
        }
        List<HolderSet<PlacedFeature>> steps = biome.get().value().getGenerationSettings().features();
        List<PlacedFeature> features = new ArrayList<>();
        List<Integer> featureSteps = new ArrayList<>();
        for (int step : STEPS) {
            if (steps.size() > step) {
                for (PlacedFeature feature : steps.get(step).stream().map(Holder::value).toList()) {
                    if (accepts(step, feature)) {
                        features.add(feature);
                        featureSteps.add(step);
                    }
                }
            }
        }
        if (features.isEmpty()) {
            return null;
        }
        int[] range = settings.range(biome.get());
        int simulatedHeight = range[1] - range[0] + 1;
        float density = settings.density(box.getYSpan(), range);
        return new OreGenerator(features, featureSteps, box, range[0], simulatedHeight, density, seed);
    }

    /**
     * Selects the features to place: everything of the underground ores step, and the ore features of the
     * underground decoration step (where the Nether keeps its ores). Features placed on a heightmap (e.g. sand and
     * clay disks below water) make no sense inside a structure and are skipped.
     */
    private static boolean accepts(int step, PlacedFeature feature) {
        if (feature.placement().stream().anyMatch(modifier -> modifier instanceof HeightmapPlacement)) {
            return false;
        }
        return step == ORE_STEP || feature.feature().value() instanceof AbstractOreFeature;
    }

    /** @return the chunk columns ({@code {x, z}}) to generate, in order */
    List<int[]> columns() {
        List<int[]> columns = new ArrayList<>();
        for (int x = SectionPos.blockToSectionCoord(this.box.minX()); x <= SectionPos.blockToSectionCoord(this.box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(this.box.minZ()); z <= SectionPos.blockToSectionCoord(this.box.maxZ()); z++) {
                columns.add(new int[] {x, z});
            }
        }
        return columns;
    }

    /**
     * Generates the ores of one chunk column.
     *
     * @param level  the theme level
     * @param chunkX the chunk x coordinate
     * @param chunkZ the chunk z coordinate
     * @return the number of placed ore features
     */
    int generate(ServerLevel level, int chunkX, int chunkZ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(this.seed));
        int minX = SectionPos.sectionToBlockCoord(chunkX);
        int minZ = SectionPos.sectionToBlockCoord(chunkZ);
        long decorationSeed = random.setDecorationSeed(this.seed, minX, minZ);
        BlockPos origin = new BlockPos(minX, this.simulatedMin, minZ);
        int placed = 0;
        for (int index = 0; index < this.features.size(); index++) {
            PlacedFeature feature = this.features.get(index);
            random.setFeatureSeed(decorationSeed, index, this.featureSteps.get(index));
            SimulatedContext context = new SimulatedContext(level, generator, feature, this.simulatedMin, this.simulatedHeight);
            List<BlockPos> positions = List.of(origin);
            for (PlacementModifier modifier : feature.placement()) {
                if (!(modifier instanceof BiomeFilter)) {
                    List<BlockPos> next = new ArrayList<>();
                    positions.forEach(pos -> modifier.modify(context, random, pos, next::add));
                    positions = next;
                }
            }
            for (BlockPos simulated : positions) {
                BlockPos pos = new BlockPos(simulated.getX(), this.realY(simulated.getY()), simulated.getZ());
                if (!this.box.isInside(pos)) {
                    continue;
                }
                for (int attempt = this.attempts(random); attempt > 0; attempt--) {
                    if (feature.feature().value().place(level, generator, random, pos)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /**
     * Maps a simulated height onto the structure: the simulated range is stretched linearly over its layers.
     */
    private int realY(int simulatedY) {
        int offset = Mth.clamp(simulatedY - this.simulatedMin, 0, this.simulatedHeight - 1);
        return this.box.minY() + (int) ((long) offset * this.box.getYSpan() / this.simulatedHeight);
    }

    /**
     * Number of feature placements for one position: the density factor's integer part plus one more with the
     * probability of its fraction.
     */
    private int attempts(WorldgenRandom random) {
        int whole = (int) this.density;
        return whole + (random.nextFloat() < this.density - whole ? 1 : 0);
    }

    /**
     * Placement context reporting the simulated height range to height providers.
     */
    private static final class SimulatedContext extends PlacementContext {

        private final int minY;
        private final int height;

        SimulatedContext(WorldGenLevel level, ChunkGenerator generator, PlacedFeature feature, int minY, int height) {
            super(level, generator, Optional.of(feature));
            this.minY = minY;
            this.height = height;
        }

        @Override
        public int getMinGenY() {
            return this.minY;
        }

        @Override
        public int getGenDepth() {
            return this.height;
        }
    }
}

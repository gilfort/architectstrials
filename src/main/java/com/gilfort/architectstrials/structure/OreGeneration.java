package com.gilfort.architectstrials.structure;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;

/**
 * Natural ore generation of a challenge (US-37), the {@code ore_generation} block of the challenge metadata:
 * <pre>{@code
 * "ore_generation": {
 *   "biome": "minecraft:plains",
 *   "min_y": -64,
 *   "max_y": 319,
 *   "density": 0.5
 * }
 * }</pre>
 * When an instance is created, the ore features of the biome's underground ores step are placed into the
 * structure as if its layers spanned the simulated height range {@code min_y..max_y} (lowest layer = {@code min_y},
 * highest = {@code max_y}). The range defaults to the biome's home dimension: 0..127 for Nether biomes, 0..255 for
 * End biomes, −64..319 otherwise. {@code density} scales the number of ore attempts; the default (structure height
 * / range height) keeps the ore density per block as in vanilla.
 *
 * @param biome   the biome whose ore features are used
 * @param minY    the simulated height of the lowest structure layer
 * @param maxY    the simulated height of the highest structure layer
 * @param density the ore attempt factor, or empty for the vanilla-equivalent default
 */
public record OreGeneration(ResourceKey<Biome> biome, Optional<Integer> minY, Optional<Integer> maxY, Optional<Float> density) {

    /** Codec of the {@code ore_generation} block. */
    public static final Codec<OreGeneration> CODEC = RecordCodecBuilder.<OreGeneration>create(instance -> instance.group(
            ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(OreGeneration::biome),
            Codec.INT.optionalFieldOf("min_y").forGetter(OreGeneration::minY),
            Codec.INT.optionalFieldOf("max_y").forGetter(OreGeneration::maxY),
            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("density").forGetter(OreGeneration::density)
    ).apply(instance, OreGeneration::new)).validate(ores -> ores.minY().isPresent() && ores.maxY().isPresent()
            && ores.minY().get() >= ores.maxY().get()
            ? DataResult.error(() -> "ore_generation min_y must be below max_y") : DataResult.success(ores));

    /**
     * Resolves the simulated height range for a biome.
     *
     * @param biome the biome
     * @return {@code {minY, maxY}}
     */
    public int[] range(Holder<Biome> biome) {
        int defaultMin = 0;
        int defaultMax;
        if (biome.is(BiomeTags.IS_NETHER)) {
            defaultMax = 127;
        } else if (biome.is(BiomeTags.IS_END)) {
            defaultMax = 255;
        } else {
            defaultMin = -64;
            defaultMax = 319;
        }
        int min = this.minY.orElse(defaultMin);
        int max = Math.max(min + 1, this.maxY.orElse(Math.max(defaultMax, min + 1)));
        return new int[] {min, max};
    }

    /**
     * Resolves the ore attempt factor: the configured density, or structure height / range height, which keeps the
     * ore density per block as in vanilla.
     *
     * @param structureHeight the number of structure layers
     * @param range           the simulated height range, see {@link #range}
     * @return the factor
     */
    public float density(int structureHeight, int[] range) {
        return this.density.orElse((float) structureHeight / (range[1] - range[0] + 1));
    }
}

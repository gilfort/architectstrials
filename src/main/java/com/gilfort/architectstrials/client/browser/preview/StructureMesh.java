package com.gilfort.architectstrials.client.browser.preview;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.QuadInstance;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * The visible block faces of a structure template, tesselated once for the preview (US-43). Built off the render
 * thread like vanilla chunk sections: neighbouring blocks cull hidden faces, so large structures shrink to their
 * surface. Lighting is uniform (full bright, vanilla face shading); entities, block entity renderers and fluids are
 * left out.
 *
 * @param quads the visible quads
 * @param size  the template size
 */
public record StructureMesh(List<Quad> quads, Vec3i size) {

    /**
     * One visible quad.
     *
     * @param x      the block offset x
     * @param y      the block offset y
     * @param z      the block offset z
     * @param quad   the baked quad
     * @param colors the vertex colors (tint and face shading)
     * @param layer  the render layer
     */
    public record Quad(float x, float y, float z, BakedQuad quad, int[] colors, ChunkSectionLayer layer) {

        /**
         * Returns the quad's instance data: its colors at full brightness.
         *
         * @return a fresh instance
         */
        public QuadInstance instance() {
            QuadInstance instance = new QuadInstance();
            for (int vertex = 0; vertex < 4; vertex++) {
                instance.setColor(vertex, this.colors[vertex]);
            }
            return instance;
        }
    }

    /**
     * Tesselates a template. Safe to call off the render thread.
     *
     * @param template the template
     * @param models   the block state models
     * @param colors   the block colors
     * @param biome    the biome used for grass, foliage and water tints
     * @return the mesh
     */
    public static StructureMesh build(StructureTemplate template, BlockStateModelSet models, BlockColors colors, @Nullable Holder<Biome> biome) {
        TemplateView view = new TemplateView(biome);
        if (!template.palettes.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo info : template.palettes.getFirst().blocks()) {
                if (!info.state().isAir() && !info.state().is(Blocks.STRUCTURE_VOID)) {
                    view.blocks.put(info.pos().asLong(), info.state());
                }
            }
        }
        ModelBlockRenderer renderer = new ModelBlockRenderer(false, true, colors);
        List<Quad> quads = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Long2ObjectOpenHashMap.Entry<BlockState> entry : view.blocks.long2ObjectEntrySet()) {
            BlockState state = entry.getValue();
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pos.set(BlockPos.of(entry.getLongKey()));
            BlockPos blockPos = pos.immutable();
            renderer.tesselateBlock((x, y, z, quad, instance) -> {
                int[] vertexColors = new int[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertexColors[vertex] = instance.getColor(vertex);
                }
                quads.add(new Quad(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z, quad, vertexColors, quad.materialInfo().layer()));
            }, 0.0F, 0.0F, 0.0F, view, blockPos, state, models.get(state), state.getSeed(blockPos));
        }
        return new StructureMesh(List.copyOf(quads), template.getSize());
    }

    /** The template's blocks as a read-only level for culling and tints; everything outside is air, fully lit. */
    private static final class TemplateView implements BlockAndTintGetter {

        private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        private final @Nullable Holder<Biome> biome;

        TemplateView(@Nullable Holder<Biome> biome) {
            this.biome = biome;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return this.biome == null ? -1 : color.getColor(this.biome.value(), pos.getX(), pos.getZ());
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.blocks.get(pos.asLong());
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 4096;
        }

        @Override
        public int getMinY() {
            return -2048;
        }
    }
}

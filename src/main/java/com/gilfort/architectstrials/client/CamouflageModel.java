package com.gilfort.architectstrials.client;

import java.util.List;
import java.util.Map;

import com.gilfort.architectstrials.block.ExitCamouflage;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

/**
 * Block model of a camouflaged exit (US-34), used for the exit block states with
 * {@link ExitCamouflage#CAMOUFLAGED} set. It draws the camouflage block taken from the block entity's model data,
 * followed by the state's own model: the overlay (exit lines while open, door and arrow on the marker, nothing
 * while locked). Without camouflage data (e.g. the block no longer exists) it draws the exit's normal look.
 * The model is baked into the chunk mesh like any block, so a camouflaged exit costs nothing per frame.
 */
public class CamouflageModel extends DelegateBlockStateModel {

    private final BlockStateModel plain;
    private final Map<BlockState, BlockStateModel> models;

    /**
     * Creates the model.
     *
     * @param overlay the model of the camouflaged state (drawn on top of the camouflage)
     * @param plain   the model of the same state without camouflage (fallback)
     * @param models  all baked block state models, to look up the camouflage block's model
     */
    public CamouflageModel(BlockStateModel overlay, BlockStateModel plain, Map<BlockState, BlockStateModel> models) {
        super(overlay);
        this.plain = plain;
        this.models = models;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockState camouflage = camouflage(level, pos);
        BlockStateModel model = this.model(camouflage);
        if (camouflage == null || model == null) {
            this.plain.collectParts(level, pos, state, random, parts);
            return;
        }
        model.collectParts(level, pos, camouflage, random, parts);
        super.collectParts(level, pos, state, random, parts);
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState camouflage = camouflage(level, pos);
        BlockStateModel model = this.model(camouflage);
        return camouflage == null || model == null ? this.plain.particleMaterial(level, pos, state) : model.particleMaterial(level, pos, camouflage);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        BlockState camouflage = camouflage(level, pos);
        return camouflage == null || this.model(camouflage) == null ? this.plain.createGeometryKey(level, pos, state, random) : new GeometryKey(state, camouflage);
    }

    private static @Nullable BlockState camouflage(BlockAndTintGetter level, BlockPos pos) {
        return level.getModelData(pos).get(ExitCamouflage.MODEL_PROPERTY);
    }

    private @Nullable BlockStateModel model(@Nullable BlockState camouflage) {
        return camouflage == null ? null : this.models.get(camouflage);
    }

    /** Geometry cache key of a camouflaged exit. */
    private record GeometryKey(BlockState exit, BlockState camouflage) {
    }
}

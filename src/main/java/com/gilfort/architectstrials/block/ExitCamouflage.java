package com.gilfort.architectstrials.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelProperty;

/**
 * Camouflage of exits (US-34): an Exit Marker, and the Challenge Exit created from it, can take over the look of
 * another full block. Purely visual; hardness, sounds, light and collision of the exit are unchanged.
 * <p>
 * The camouflage is a block state stored in the block entity. It is synced to clients, where the exit's block
 * model draws the camouflage block (and the exit's overlay) instead of its own look.
 */
public final class ExitCamouflage {

    private static final String TAG = "camouflage";
    private static final String SYNC_TAG = "camouflage_sync";

    /**
     * Block state property of both exit blocks: whether a camouflage is set. It selects the overlay model (exit
     * lines, marker direction arrow) that is drawn on top of the camouflage block.
     */
    public static final BooleanProperty CAMOUFLAGED = BooleanProperty.create("camouflaged");

    /** Model data key of the camouflage, read by the client-side exit model. */
    public static final ModelProperty<BlockState> MODEL_PROPERTY = new ModelProperty<>();

    private ExitCamouflage() {
    }

    /**
     * A block entity with an optional camouflage.
     */
    public interface Holder {

        /** @return the camouflage block state, if any */
        Optional<BlockState> camouflage();

        /**
         * Sets or removes the camouflage and syncs it to clients.
         *
         * @param camouflage the camouflage block state, or empty for the normal look
         */
        void setCamouflage(Optional<BlockState> camouflage);
    }

    /**
     * Checks whether a block state may be used as camouflage: an opaque full cube rendered by a block model,
     * without block entity or fluid, and not an exit itself.
     *
     * @param state the candidate block state
     * @param level the level, for the shape
     * @param pos   the position, for the shape
     * @return {@code true} if the state is allowed
     */
    public static boolean isAllowed(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getRenderShape() == RenderShape.MODEL
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.canOcclude()
                && !(state.getBlock() instanceof ExitMarkerBlock)
                && !(state.getBlock() instanceof ChallengeExitBlock)
                && Block.isShapeFullBlock(state.getShape(level, pos));
    }

    /**
     * Rotates a camouflage with the structure it is placed in, so that e.g. logs keep their axis relative to the
     * room.
     *
     * @param camouflage the camouflage
     * @param rotation   the structure rotation
     * @return the rotated camouflage
     */
    public static Optional<BlockState> rotate(Optional<BlockState> camouflage, Rotation rotation) {
        return camouflage.map(state -> state.rotate(rotation));
    }

    /**
     * Reads a stored camouflage. A camouflage block that no longer exists (removed mod) falls back to the normal
     * look; the block entity loader logs the skipped value.
     *
     * @param input the block entity data
     * @return the camouflage, if present and valid
     */
    public static Optional<BlockState> read(ValueInput input) {
        return input.read(TAG, BlockState.CODEC).filter(state -> !state.isAir());
    }

    /**
     * Writes a camouflage.
     *
     * @param output     the block entity data
     * @param camouflage the camouflage
     */
    public static void write(ValueOutput output, Optional<BlockState> camouflage) {
        camouflage.ifPresent(state -> output.store(TAG, BlockState.CODEC, state));
    }

    /**
     * Creates the client sync data: the camouflage plus a marker, so that removing the camouflage is synced too
     * (an empty update tag would be ignored by the client).
     *
     * @param camouflage the camouflage
     * @return the update tag
     */
    public static CompoundTag syncTag(Optional<BlockState> camouflage) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_TAG, true);
        camouflage.ifPresent(state -> tag.store(TAG, BlockState.CODEC, state));
        return tag;
    }

    /**
     * Updates the {@link #CAMOUFLAGED} property after the camouflage changed, then sends the new camouflage to
     * clients and lets them redraw the block.
     *
     * @param blockEntity the block entity whose camouflage changed
     * @param camouflaged whether a camouflage is set now
     */
    public static void changed(BlockEntity blockEntity, boolean camouflaged) {
        blockEntity.setChanged();
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(CAMOUFLAGED) && state.getValue(CAMOUFLAGED) != camouflaged) {
            level.setBlock(pos, state.setValue(CAMOUFLAGED, camouflaged), Block.UPDATE_CLIENTS);
        }
        BlockState updated = level.getBlockState(pos);
        level.sendBlockUpdated(pos, updated, updated, Block.UPDATE_CLIENTS);
    }

    /**
     * Client side: after new camouflage data arrived, refreshes the model data and redraws the block.
     *
     * @param blockEntity the block entity that loaded new data
     */
    public static void loaded(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level != null && level.isClientSide()) {
            blockEntity.requestModelDataUpdate();
            BlockState state = blockEntity.getBlockState();
            level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_IMMEDIATE);
        }
    }
}

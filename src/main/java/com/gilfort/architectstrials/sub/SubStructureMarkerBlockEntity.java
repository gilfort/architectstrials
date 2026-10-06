package com.gilfort.architectstrials.sub;

import java.util.Arrays;

import com.gilfort.architectstrials.registry.ModBlockEntityTypes;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity of the {@link SubStructureMarkerBlock} (US-32): the {@link SubStructureSetup} and the offset area.
 * Saved with the structure; synced to clients for the marker GUI.
 */
public class SubStructureMarkerBlockEntity extends BlockEntity {

    /** Number of offsets: −X, +X, −Y, +Y, −Z, +Z. */
    public static final int OFFSETS = 6;

    /** Largest offset in any direction. */
    public static final int MAX_OFFSET = 64;

    private static final String SETUP_TAG = "sub_structures";
    private static final String OFFSETS_TAG = "offsets";

    private SubStructureSetup setup = SubStructureSetup.EMPTY;
    private int[] offsets = new int[OFFSETS];

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public SubStructureMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SUB_STRUCTURE_MARKER.get(), pos, state);
    }

    /** @return the sub structure setup */
    public SubStructureSetup setup() {
        return this.setup;
    }

    /**
     * Sets the sub structure setup.
     *
     * @param setup the setup
     */
    public void setSetup(SubStructureSetup setup) {
        this.setup = setup;
        this.changed();
    }

    /**
     * Returns the offset area around the marker, in the unrotated editor view.
     *
     * @return a copy of the offsets −X, +X, −Y, +Y, −Z, +Z (each 0–{@value #MAX_OFFSET})
     */
    public int[] offsets() {
        return this.offsets.clone();
    }

    /**
     * Sets the offset area; values are clamped to 0–{@value #MAX_OFFSET}.
     *
     * @param offsets the offsets −X, +X, −Y, +Y, −Z, +Z
     */
    public void setOffsets(int[] offsets) {
        this.offsets = clamp(offsets);
        this.changed();
    }

    private static int[] clamp(int[] offsets) {
        int[] clamped = new int[OFFSETS];
        for (int i = 0; i < OFFSETS && i < offsets.length; i++) {
            clamped[i] = Math.clamp(offsets[i], 0, MAX_OFFSET);
        }
        return clamped;
    }

    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.setup = input.read(SETUP_TAG, SubStructureSetup.CODEC).orElse(SubStructureSetup.EMPTY);
        this.offsets = clamp(input.read(OFFSETS_TAG, Codec.INT.listOf())
                .map(list -> list.stream().mapToInt(Integer::intValue).toArray()).orElse(new int[OFFSETS]));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.setup.isEmpty()) {
            output.store(SETUP_TAG, SubStructureSetup.CODEC, this.setup);
        }
        if (Arrays.stream(this.offsets).anyMatch(offset -> offset != 0)) {
            output.store(OFFSETS_TAG, Codec.INT.listOf(), Arrays.stream(this.offsets).boxed().toList());
        }
    }
}

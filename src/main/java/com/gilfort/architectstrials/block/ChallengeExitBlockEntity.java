package com.gilfort.architectstrials.block;

import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Data-less block entity of the {@link ChallengeExitBlock}. It only exists so the client can render the
 * animated portal surface of an {@link ExitGroup} with a block entity renderer.
 */
public class ChallengeExitBlockEntity extends BlockEntity {

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public ChallengeExitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHALLENGE_EXIT.get(), pos, state);
    }
}

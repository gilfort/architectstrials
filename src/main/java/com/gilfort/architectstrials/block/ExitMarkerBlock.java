package com.gilfort.architectstrials.block;

import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Editor marker for a challenge exit. Inert while building; when the structure is placed, {@link #resolve}
 * turns it into a functional {@link ChallengeExitBlock} with the same facing and records it on the instance.
 * The facing determines the orientation of the exit portal plane (it faces the builder when placed).
 */
public class ExitMarkerBlock extends HorizontalDirectionalBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ExitMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Marker resolver: replaces the marker by a functional exit (locked if already powered) and records it.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolve(MarkerContext context, BlockPos pos) {
        BlockState marker = context.level().getBlockState(pos);
        Direction facing = marker.getBlock() instanceof ExitMarkerBlock ? marker.getValue(FACING) : Direction.NORTH;
        BlockState exit = ModBlocks.CHALLENGE_EXIT.get().defaultBlockState()
                .setValue(ChallengeExitBlock.FACING, facing)
                .setValue(ChallengeExitBlock.POWERED, context.level().hasNeighborSignal(pos));
        context.level().setBlock(pos, exit, Block.UPDATE_ALL);
        context.addExit(pos.immutable());
    }
}

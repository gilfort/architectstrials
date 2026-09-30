package com.gilfort.architectstrials.block;

import com.gilfort.architectstrials.instance.SpawnPoint;
import com.gilfort.architectstrials.marker.MarkerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Editor marker for player entry points. Builders place it where players may enter a challenge; it faces the
 * direction the builder looked when placing it, which becomes the players' viewing direction.
 * <p>
 * Never appears in play: when the structure is placed, {@link #resolve} records the position and facing as a
 * {@link SpawnPoint} and replaces the marker with air.
 */
public class PlayerSpawnMarkerBlock extends HorizontalDirectionalBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public PlayerSpawnMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Marker resolver: records the spawn point and removes the marker.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolve(MarkerContext context, BlockPos pos) {
        BlockState state = context.level().getBlockState(pos);
        if (state.getBlock() instanceof PlayerSpawnMarkerBlock) {
            context.addSpawnPoint(new SpawnPoint(pos.immutable(), state.getValue(FACING)));
        }
        context.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}

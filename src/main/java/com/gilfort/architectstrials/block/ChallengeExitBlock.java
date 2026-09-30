package com.gilfort.architectstrials.block;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.portal.PortalParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;

/**
 * The functional challenge exit, created from an {@link ExitMarkerBlock} when a structure is placed.
 * <p>
 * It is a 1×1 base; the exit portal is the 1×2 space directly above it. <strong>A redstone signal locks the
 * exit</strong>: unpowered, a portal swirl is visible above the base and players walking through it complete
 * the run; powered, only the base is visible and nothing happens. Builders use this to gate completion (e.g.
 * power is removed once all enemies are defeated). Walk-through detection lives in the exit handler.
 */
public class ChallengeExitBlock extends HorizontalDirectionalBlock {

    /** Whether a redstone signal is present, i.e. the exit is locked. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ChallengeExitBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    /**
     * Checks whether an exit block is open.
     *
     * @param state the block state
     * @return {@code true} if the state is an unpowered challenge exit
     */
    public static boolean isOpen(BlockState state) {
        return state.getBlock() instanceof ChallengeExitBlock && !state.getValue(POWERED);
    }

    /**
     * Returns the 1×2 portal space above an exit base.
     *
     * @param pos the position of the exit base
     * @return the area players must walk through
     */
    public static AABB portalArea(BlockPos pos) {
        return new AABB(pos.above()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            PortalParticles.swirl(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    state.getValue(FACING).toYRot(), random, 6);
        }
    }
}

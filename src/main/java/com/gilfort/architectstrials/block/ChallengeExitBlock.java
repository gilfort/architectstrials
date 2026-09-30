package com.gilfort.architectstrials.block;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.portal.PortalParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * It is a 1×1 base; adjacent bases combine into one larger portal (see {@link ExitGroup}). <strong>A redstone
 * signal locks the exit</strong>: unpowered, an animated portal surface is shown above the bases and players
 * walking through it complete the run; powered, only the bases are visible and nothing happens. Builders use
 * this to gate completion (e.g. power is removed once all enemies are defeated). Walk-through detection lives
 * in the run completion handler; the surface is drawn by a block entity renderer.
 */
public class ChallengeExitBlock extends HorizontalDirectionalBlock implements EntityBlock {

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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChallengeExitBlockEntity(pos, state);
    }

    /**
     * A few accent particles on open portals; the surface itself is rendered, not built from particles.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ExitGroup.find(level, pos).filter(group -> !group.locked() && group.anchor().equals(pos)).ifPresent(group -> {
            AABB area = group.portalArea();
            if (random.nextInt(3) == 0) {
                PortalParticles.swirl(level, area.getCenter().x, area.minY, area.getCenter().z, state.getValue(FACING).toYRot(), random, 1);
            }
        });
    }
}

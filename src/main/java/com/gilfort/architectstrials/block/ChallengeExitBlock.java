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
 * this to gate completion (e.g. power is removed once all enemies are defeated). Exits set to require the
 * instance's required mobs are additionally <em>sealed</em> until those are defeated (US-30), with the same look. Walk-through detection lives
 * in the run completion handler; the surface is drawn by a block entity renderer.
 */
public class ChallengeExitBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Whether a redstone signal is present, i.e. the exit is locked. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Whether the exit is sealed until all required mobs of its instance are defeated (US-30). */
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ChallengeExitBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, SEALED);
    }

    /**
     * Checks whether an exit block is open.
     *
     * @param state the block state
     * @return {@code true} if the state is an unpowered challenge exit
     */
    public static boolean isOpen(BlockState state) {
        return state.getBlock() instanceof ChallengeExitBlock && !state.getValue(POWERED) && !state.getValue(SEALED);
    }

    /**
     * Seals an exit if its block entity requires the required mobs of the instance.
     *
     * @param level the level
     * @param pos   the exit base position
     */
    public static void sealIfRequiringMobs(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChallengeExitBlockEntity exit && exit.requiresMobs()) {
            setSealed(level, pos, true);
        }
    }

    /**
     * Seals or unseals an exit base; nothing happens if there is no exit at the position.
     *
     * @param level  the level
     * @param pos    the exit base position
     * @param sealed whether the exit is sealed
     */
    public static void setSealed(Level level, BlockPos pos, boolean sealed) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChallengeExitBlock && state.getValue(SEALED) != sealed) {
            level.setBlock(pos, state.setValue(SEALED, sealed), Block.UPDATE_CLIENTS);
        }
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

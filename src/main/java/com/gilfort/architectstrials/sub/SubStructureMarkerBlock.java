package com.gilfort.architectstrials.sub;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Editor marker that places a sub structure (US-32) when the challenge structure is placed; see
 * {@link SubStructurePlacer}. It faces the direction the builder looked in when placing it: facing north places
 * the sub structure as built, east turns it 90° clockwise, and so on. The sub structure's origin corner (its
 * lowest north-west block as built) sits at the marker, or at a random block of the marker's offset area.
 */
public class SubStructureMarkerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public SubStructureMarkerBlock(BlockBehaviour.Properties properties) {
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubStructureMarkerBlockEntity(pos, state);
    }

    /**
     * Opens the marker GUI for builders (creative mode with operator permissions).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Opens the marker GUI.
     *
     * @param player the player
     * @param pos    the marker position
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SubStructureMarkerMenu(id, pos),
                Component.translatable("block.architectstrials.sub_structure_marker")), buffer -> buffer.writeBlockPos(pos));
    }

    /**
     * Returns the rotation of the sub structure for a marker facing.
     *
     * @param facing the marker facing in the world
     * @return the rotation (north = as built)
     */
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}

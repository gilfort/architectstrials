package com.gilfort.architectstrials.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Editor marker for enemies. Used by both the Direct Spawn Marker and the Spawner Marker; they only differ in
 * the resolver registered for them (see {@link com.gilfort.architectstrials.marker.SpawnMarkerResolvers}).
 * <p>
 * Right-clicking opens the marker's container (spawn egg + equipment). Only players who may use game master
 * blocks (creative mode with operator permissions) can open it.
 */
public class SpawnMarkerBlock extends Block implements EntityBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public SpawnMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker) {
            player.openMenu(marker);
        }
        return InteractionResult.SUCCESS;
    }
}

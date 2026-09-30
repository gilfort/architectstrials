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
 * Editor marker that becomes a vanilla trial spawner (see
 * {@link com.gilfort.architectstrials.marker.TrialSpawnerMarkerResolver}). Right-clicking opens its container
 * (three mob rows and the number of simultaneous mobs); the reward loot table is set with
 * {@code /architectstrials marker loot_table <id>}. Only players who may use game master blocks can open it.
 */
public class TrialSpawnerMarkerBlock extends Block implements EntityBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public TrialSpawnerMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrialSpawnerMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrialSpawnerMarkerBlockEntity marker) {
            player.openMenu(marker);
        }
        return InteractionResult.SUCCESS;
    }
}

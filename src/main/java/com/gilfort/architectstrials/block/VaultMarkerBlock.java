package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetups;
import com.gilfort.architectstrials.marker.MarkerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Editor marker that becomes a vanilla vault (US-26): rewards behind a key, one share per player and instance.
 * Right-clicking opens its settings (normal / ominous, key slot); the reward is a loot setup composed with the loot
 * tool, falling back to a loot table ({@code /at marker loot_table <id>}) or the vanilla trial chamber reward.
 */
public class VaultMarkerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public VaultMarkerBlock(BlockBehaviour.Properties properties) {
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VaultMarkerBlockEntity marker) {
            player.openMenu(marker);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns the key a vault of the given variant opens with when the marker's key slot is empty.
     *
     * @param ominous whether the vault is ominous
     * @return the vanilla trial key or ominous trial key
     */
    public static ItemStack defaultKey(boolean ominous) {
        return new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY);
    }

    /**
     * Marker resolver: replaces the marker by a vanilla vault with the marker's variant, key and reward. A loot setup
     * is rolled through the {@link LootSetups#PLACEHOLDER} loot table (per player on unlock, and for the floating
     * preview), so it is stored in the vault's persistent data.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolve(MarkerContext context, BlockPos pos) {
        Level level = context.level();
        BlockState marker = level.getBlockState(pos);
        Direction facing = marker.getBlock() instanceof VaultMarkerBlock ? marker.getValue(FACING) : Direction.NORTH;
        boolean ominous = false;
        ItemStack key = ItemStack.EMPTY;
        LootSetup setup = LootSetup.EMPTY;
        var table = BuiltInLootTables.TRIAL_CHAMBERS_REWARD;
        if (level.getBlockEntity(pos) instanceof VaultMarkerBlockEntity entity) {
            ominous = entity.ominous();
            key = entity.key();
            setup = entity.lootSetup(false);
            table = entity.lootTableReference().orElse(ominous ? BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS : BuiltInLootTables.TRIAL_CHAMBERS_REWARD);
        }
        BlockState vault = Blocks.VAULT.defaultBlockState().setValue(VaultBlock.FACING, facing).setValue(VaultBlock.OMINOUS, ominous);
        level.setBlock(pos, vault, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof VaultBlockEntity vaultEntity) {
            VaultConfig defaults = VaultConfig.DEFAULT;
            vaultEntity.setConfig(new VaultConfig(setup.isEmpty() ? table : LootSetups.PLACEHOLDER, defaults.activationRange(),
                    defaults.deactivationRange(), key.isEmpty() ? defaultKey(ominous) : key.copy(), Optional.empty()));
            if (!setup.isEmpty()) {
                LootSetups.write(vaultEntity, LootSetups.SETUP_KEY, setup);
            }
            vaultEntity.setChanged();
        }
    }
}

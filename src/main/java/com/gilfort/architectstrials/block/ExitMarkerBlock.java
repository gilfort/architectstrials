package com.gilfort.architectstrials.block;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupHolder;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Editor marker for a challenge exit. Inert while building; when the structure is placed, {@link #resolve}
 * turns it into a functional {@link ChallengeExitBlock} with the same facing and records it on the instance.
 * The facing determines the orientation of the exit portal plane (it faces the builder when placed). An
 * optional completion bonus loot table set on the marker is carried over to the exit.
 */
public class ExitMarkerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ExitMarkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ExitCamouflage.CAMOUFLAGED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ExitCamouflage.CAMOUFLAGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExitMarkerBlockEntity(pos, state);
    }

    /**
     * Opens the exit's settings for builders (creative mode with operator permissions) on a right click with an
     * empty hand.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExitMarkerBlockEntity marker) {
            player.openMenu(marker);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Camouflage (US-34): builders right-clicking the marker with a block take over its look, using the block state
     * it would be placed as. Shift + right click skips this (vanilla) and places the block as usual; other items
     * fall through to {@link #useWithoutItem}.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !player.canUseGameMasterBlocks()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState camouflage = blockItem.getBlock().getStateForPlacement(new BlockPlaceContext(player, hand, stack, hitResult));
        if (!camouflage(level, pos, camouflage)) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.camouflage.invalid", stack.getHoverName())
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(Component.translatable("message.architectstrials.camouflage.set", camouflage.getBlock().getName()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets the camouflage of an Exit Marker if the block state is allowed (see {@link ExitCamouflage#isAllowed}).
     *
     * @param level      the level
     * @param pos        the marker position
     * @param camouflage the block state to take the look of, or {@code null}
     * @return {@code true} if the camouflage was set
     */
    public static boolean camouflage(Level level, BlockPos pos, @Nullable BlockState camouflage) {
        if (camouflage == null || !ExitCamouflage.isAllowed(camouflage, level, pos)
                || !(level.getBlockEntity(pos) instanceof ExitMarkerBlockEntity marker)) {
            return false;
        }
        marker.setCamouflage(Optional.of(camouflage));
        return true;
    }

    /**
     * Marker resolver: replaces the marker by a functional exit (locked if already powered), carries over its
     * bonus loot table reference, loot setup, "requires required mobs" setting and camouflage (rotated with the
     * structure) and records it.
     *
     * @param context the placement context
     * @param pos     the world position of the marker (already transformed)
     */
    public static void resolve(MarkerContext context, BlockPos pos) {
        BlockState marker = context.level().getBlockState(pos);
        Optional<ResourceKey<LootTable>> bonus = context.level().getBlockEntity(pos) instanceof LootTableReference reference
                ? reference.lootTableReference() : Optional.empty();
        LootSetup setup = context.level().getBlockEntity(pos) instanceof LootSetupHolder holder ? holder.lootSetup(false) : LootSetup.EMPTY;
        boolean requiresMobs = context.level().getBlockEntity(pos) instanceof ExitMarkerBlockEntity exitMarker && exitMarker.requiresMobs();
        Optional<BlockState> camouflage = context.level().getBlockEntity(pos) instanceof ExitMarkerBlockEntity camouflaged
                ? ExitCamouflage.rotate(camouflaged.camouflage(), context.instance().rotation()) : Optional.empty();
        Direction facing = marker.getBlock() instanceof ExitMarkerBlock ? marker.getValue(FACING) : Direction.NORTH;
        BlockState exit = ModBlocks.CHALLENGE_EXIT.get().defaultBlockState()
                .setValue(ChallengeExitBlock.FACING, facing)
                .setValue(ChallengeExitBlock.POWERED, context.level().hasNeighborSignal(pos));
        context.level().setBlock(pos, exit, Block.UPDATE_ALL);
        if (context.level().getBlockEntity(pos) instanceof ChallengeExitBlockEntity exitEntity) {
            exitEntity.setLootTableReference(bonus);
            exitEntity.setLootSetup(false, setup);
            exitEntity.setRequiresMobs(requiresMobs);
            exitEntity.setCamouflage(camouflage);
        }
        context.addExit(pos.immutable());
    }
}

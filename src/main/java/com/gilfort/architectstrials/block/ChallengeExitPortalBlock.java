package com.gilfort.architectstrials.block;

import java.util.Collection;
import java.util.Optional;

import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.RequiredMobs;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.run.RunCompletion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible block filling the portal space above a group of challenge exits (US-38).
 * <p>
 * It has no collision, no outline and no look of its own (the surface is drawn by the exit's block entity
 * renderer) and cannot be broken or replaced. Vanilla calls {@link #entityInside} only while an entity touches it,
 * so run completion needs no per-tick check: the lock of the exit group is checked on contact, and locked exits
 * keep their portal blocks.
 */
public class ChallengeExitPortalBlock extends Block {

    /** Ticks between two "defeat all marked enemies" messages while a player stands in a sealed exit. */
    private static final int MESSAGE_INTERVAL_TICKS = 20;

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ChallengeExitPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
            boolean pastEdges) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            onPlayerInside(serverLevel, pos, player);
        }
    }

    /**
     * Completes the run of a player touching a portal block, if the exit group below is open and belongs to the
     * player's instance.
     *
     * @param level  the challenge level
     * @param pos    the position of the portal block
     * @param player the player
     * @return {@code true} if the run was completed
     */
    public static boolean onPlayerInside(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.level() != level || !player.hasData(ModAttachments.ENTRY_POINT)) {
            return false;
        }
        Optional<ExitGroup> group = groupBelow(level, pos);
        if (group.isEmpty() || !group.get().portalArea().contains(Vec3.atCenterOf(pos))) {
            return false;
        }
        Optional<ChallengeInstance> instance = InstanceManager.findAt(level, pos)
                .filter(found -> group.get().members().stream().anyMatch(found.exits()::contains));
        if (instance.isEmpty()) {
            return false;
        }
        if (group.get().locked()) {
            if (group.get().sealed() && player.tickCount % MESSAGE_INTERVAL_TICKS == 0) {
                RequiredMobs required = instance.get().requiredMobs();
                player.sendOverlayMessage(Component.translatable("message.architectstrials.required.progress", required.defeated(), required.total()));
            }
            return false;
        }
        RunCompletion.completeThrough(player, level, instance.get(), group.get());
        return true;
    }

    /**
     * Finds the exit group whose portal space contains a portal block: the first exit base below the column of
     * portal blocks.
     */
    private static Optional<ExitGroup> groupBelow(ServerLevel level, BlockPos pos) {
        BlockPos base = pos.below();
        for (int i = 0; i <= ExitGroup.MAX_WIDTH && level.getBlockState(base).getBlock() instanceof ChallengeExitPortalBlock; i++) {
            base = base.below();
        }
        return ExitGroup.find(level, base);
    }

    /**
     * Fills the portal space of every exit group with portal blocks. Only air is replaced; blocks the builder put
     * there stay.
     *
     * @param level the challenge level
     * @param exits the positions of exit bases
     */
    public static void fill(ServerLevel level, Collection<BlockPos> exits) {
        BlockState portal = ModBlocks.CHALLENGE_EXIT_PORTAL.get().defaultBlockState();
        for (BlockPos exit : exits) {
            ExitGroup.find(level, exit).ifPresent(group -> {
                BlockPos last = group.anchor().relative(ExitGroup.along(group.facing()), group.width() - 1);
                for (BlockPos pos : BlockPos.betweenClosed(group.anchor().above(), last.above(group.height()))) {
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
                    }
                }
            });
        }
    }
}

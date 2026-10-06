package com.gilfort.architectstrials.instance;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitBlock;
import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Keeps the blocks a challenge needs to work intact in Survival challenges (US-31): exit bases, exit portal
 * blocks and the block directly below every player spawn point. Everything else may be mined, blown up and
 * built on freely. Event-based only; nothing is checked while nobody breaks blocks.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeProtection {

    private ChallengeProtection() {
    }

    /**
     * Cancels breaking a protected block.
     *
     * @param event the break event
     */
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Removes protected blocks from the blocks an explosion destroys.
     *
     * @param event the detonate event
     */
    @SubscribeEvent
    static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && ChallengeThemes.isChallengeDimension(level.dimension())) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
        }
    }

    /**
     * Checks whether a block of a challenge must not be removed.
     *
     * @param level the level
     * @param pos   the block position
     * @return {@code true} for exit bases, exit portal blocks and blocks directly below a spawn point
     */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (!ChallengeThemes.isChallengeDimension(level.dimension())) {
            return false;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof ChallengeExitBlock || block instanceof ChallengeExitPortalBlock) {
            return true;
        }
        BlockPos above = pos.above();
        return InstanceManager.findAt(level, pos)
                .map(instance -> instance.spawnPoints().stream().anyMatch(point -> point.pos().equals(above)))
                .orElse(false);
    }
}

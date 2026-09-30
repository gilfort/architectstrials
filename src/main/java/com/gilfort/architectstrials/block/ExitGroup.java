package com.gilfort.architectstrials.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A group of challenge exit bases that form one combined portal.
 * <p>
 * Exit bases directly next to each other in a straight line across their facing, all with the same facing,
 * belong together. {@code n} bases form one portal of {@code n × (n + 1)} blocks above them; lines longer than
 * {@link #MAX_WIDTH} are split into consecutive groups. <strong>A redstone signal at any base locks the whole
 * group.</strong>
 *
 * @param anchor the base with the lowest coordinate along the line; renders the portal
 * @param facing the shared facing of all bases
 * @param width  the number of bases in the group ({@code 1..MAX_WIDTH})
 * @param locked whether any base of the group is powered
 */
public record ExitGroup(BlockPos anchor, Direction facing, int width, boolean locked) {

    /** Maximum number of bases combined into one portal. */
    public static final int MAX_WIDTH = 3;

    /**
     * Finds the group an exit base belongs to.
     *
     * @param level the level
     * @param pos   the position of an exit base
     * @return the group, or empty if there is no exit base at {@code pos}
     */
    public static Optional<ExitGroup> find(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChallengeExitBlock)) {
            return Optional.empty();
        }
        Direction facing = state.getValue(ChallengeExitBlock.FACING);
        Direction along = along(facing);
        BlockPos lineStart = pos;
        int index = 0;
        while (isExit(level, lineStart.relative(along.getOpposite()), facing)) {
            lineStart = lineStart.relative(along.getOpposite());
            index++;
        }
        int lineLength = index + 1;
        while (isExit(level, lineStart.relative(along, lineLength), facing)) {
            lineLength++;
        }
        int groupStart = index / MAX_WIDTH * MAX_WIDTH;
        int width = Math.min(MAX_WIDTH, lineLength - groupStart);
        BlockPos anchor = lineStart.relative(along, groupStart);
        boolean locked = false;
        for (int i = 0; i < width; i++) {
            locked |= level.getBlockState(anchor.relative(along, i)).getValue(ChallengeExitBlock.POWERED);
        }
        return Optional.of(new ExitGroup(anchor, facing, width, locked));
    }

    /**
     * Returns the positive horizontal direction across a facing, i.e. the direction the bases line up in.
     *
     * @param facing the facing of the bases
     * @return east for north/south facing, south for east/west facing
     */
    public static Direction along(Direction facing) {
        Direction clockWise = facing.getClockWise();
        return clockWise.getAxisDirection() == Direction.AxisDirection.POSITIVE ? clockWise : clockWise.getOpposite();
    }

    /** @return the height of the portal in blocks */
    public int height() {
        return this.width + 1;
    }

    /** @return the positions of all bases of the group */
    public List<BlockPos> members() {
        List<BlockPos> members = new ArrayList<>(this.width);
        for (int i = 0; i < this.width; i++) {
            members.add(this.anchor.relative(along(this.facing), i));
        }
        return members;
    }

    /** @return the portal space above the bases that players walk through */
    public AABB portalArea() {
        BlockPos last = this.anchor.relative(along(this.facing), this.width - 1);
        return AABB.encapsulatingFullBlocks(this.anchor.above(), last.above(this.height()));
    }

    private static boolean isExit(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChallengeExitBlock && state.getValue(ChallengeExitBlock.FACING) == facing;
    }
}

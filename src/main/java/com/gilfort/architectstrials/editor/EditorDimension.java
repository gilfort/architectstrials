package com.gilfort.architectstrials.editor;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The shared editor dimension {@code architectstrials:editor}: a void world in which builders build exactly one
 * structure together. A world border limits it to the maximum structure footprint (128 × 128), so nobody can
 * build larger than allowed. A 3×3 {@link EditorPlatformBlock} at the center gives builders something to start
 * from; it is ignored when saving.
 * <p>
 * All methods take the level explicitly so they can be exercised on other levels in tests.
 */
public final class EditorDimension {

    /** Key of the editor dimension. */
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ArchitectsTrials.id("editor"));

    /** Half the edge length of the buildable area. */
    public static final int HALF_SIZE = Slot.MAX_STRUCTURE_SIZE / 2;

    private static final int PLATFORM_RADIUS = 1;

    private EditorDimension() {
    }

    /**
     * Checks whether a dimension is the editor dimension.
     *
     * @param dimension the dimension key
     * @return {@code true} for the editor dimension
     */
    public static boolean isEditor(ResourceKey<Level> dimension) {
        return KEY.equals(dimension);
    }

    /**
     * Returns the buildable area of the editor, which is also what {@link #clear} empties.
     *
     * @param level the editor level
     * @return the 128 × 128 area around the origin over the full build height
     */
    public static BoundingBox area(ServerLevel level) {
        return new BoundingBox(-HALF_SIZE, level.getMinY(), -HALF_SIZE, HALF_SIZE - 1, level.getMaxY(), HALF_SIZE - 1);
    }

    /**
     * Returns the Y level of the start platform: one below the height structures are placed at, so what builders
     * put on the platform ends up at the placement height.
     *
     * @return the platform Y level
     */
    public static int platformY() {
        return ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt() - 1;
    }

    /**
     * Returns the position players stand on when entering or after a clear.
     *
     * @return the bottom center of the block above the platform center
     */
    public static Vec3 spawnPosition() {
        return new Vec3(0.5, platformY() + 1, 0.5);
    }

    /**
     * Limits the level to the buildable area with its world border (per dimension in 26.3).
     *
     * @param level the editor level
     */
    public static void applyWorldBorder(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(Slot.MAX_STRUCTURE_SIZE);
    }

    /**
     * Places the 3×3 start platform at the center if its center block is missing.
     *
     * @param level the editor level
     */
    public static void ensurePlatform(ServerLevel level) {
        BlockPos center = new BlockPos(0, platformY(), 0);
        if (level.getBlockState(center).is(ModBlocks.EDITOR_PLATFORM.get())) {
            return;
        }
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                level.setBlock(center.offset(x, 0, z), ModBlocks.EDITOR_PLATFORM.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Moves a player into the editor: stores their entry point (no Adventure mode, no death protection),
     * creates the platform on first entry and puts them on it. Their game mode stays unchanged.
     *
     * @param player the player
     * @param level  the editor level
     */
    public static void enter(ServerPlayer player, ServerLevel level) {
        applyWorldBorder(level);
        ensurePlatform(level);
        ChallengeTravel.enter(player, level, spawnPosition(), player.getYRot(), player.getXRot(), false);
    }

    /**
     * Empties the editor: removes all blocks, block entities and entities over several ticks, then rebuilds the
     * platform and puts every player in the level onto it.
     *
     * @param level  the editor level
     * @param onDone called once the editor is empty again
     */
    public static void clear(ServerLevel level, Consumer<ServerLevel> onDone) {
        SlotManager.clearArea(level, area(level), clearedLevel -> {
            ensurePlatform(clearedLevel);
            Vec3 spawn = spawnPosition();
            for (ServerPlayer player : List.copyOf(clearedLevel.players())) {
                player.teleportTo(spawn.x, spawn.y, spawn.z);
                player.resetFallDistance();
            }
            onDone.accept(clearedLevel);
        });
    }
}

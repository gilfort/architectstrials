package com.gilfort.architectstrials.gametest;

import com.gilfort.architectstrials.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers for GameTests that work with mock players.
 * <p>
 * {@code minecraft:the_nether} acts as challenge dimension (declared as theme by the test datapack).
 */
final class TestPlayers {

    /** Position inside the challenge dimension that tests move players to. */
    static final Vec3 CHALLENGE_POSITION = new Vec3(0.5, 100.0, 0.5);

    /** Relative start position of mock players in the overworld test area. */
    static final BlockPos START = new BlockPos(1, 1, 1);

    private TestPlayers() {
    }

    /**
     * Creates a mock player standing on a guaranteed free, grounded start position in the test area.
     * The player's client is marked as loaded so the player can take damage.
     *
     * @param helper   the test helper
     * @param gameType the game mode of the player
     * @return the mock player
     */
    static ServerPlayer atStart(GameTestHelper helper, GameType gameType) {
        helper.setBlock(START.below(), Blocks.STONE);
        helper.setBlock(START, Blocks.AIR);
        helper.setBlock(START.above(), Blocks.AIR);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        player.setGameMode(gameType);
        teleport(player, helper.getLevel(), Vec3.atBottomCenterOf(helper.absolutePos(START)));
        return player;
    }

    /**
     * Teleports a player and completes the dimension change immediately, as a real client would
     * acknowledge it. Without this, players are invulnerable while "changing dimension".
     *
     * @param player   the player
     * @param level    the target level
     * @param position the target position
     */
    static void teleport(ServerPlayer player, ServerLevel level, Vec3 position) {
        player.teleport(new TeleportTransition(level, position, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
        player.hasChangedDimension();
    }

    /**
     * Returns the level acting as challenge dimension in tests.
     *
     * @param helper the test helper
     * @return the nether level
     */
    static ServerLevel challengeLevel(GameTestHelper helper) {
        return helper.getLevel().getServer().getLevel(Level.NETHER);
    }

    /**
     * Removes the mock player and marks the test as succeeded.
     *
     * @param helper the test helper
     * @param player the mock player
     */
    static void finish(GameTestHelper helper, ServerPlayer player) {
        player.removeData(ModAttachments.ENTRY_POINT);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}

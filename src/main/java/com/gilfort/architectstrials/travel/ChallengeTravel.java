package com.gilfort.architectstrials.travel;

import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorDimension;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.ScrollEffectApplication;
import com.gilfort.architectstrials.portal.PortalEcho;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Central service for moving players into and out of Architect's Trials dimensions.
 * <p>
 * Entering stores the player's {@link EntryPoint}; returning always brings the player back to exactly that
 * point. Every way out of a challenge (death protection, exit marker, time limit, login handling, the exit
 * command) goes through {@link #returnToEntryPoint(ServerPlayer)}.
 * <p>
 * Only teleports of this class may enter challenge and editor dimensions (US-44); see
 * {@link #asModTravel(Runnable)}.
 */
public final class ChallengeTravel {

    /** Portal cooldown applied after returning, so players do not walk straight back into an open portal. */
    public static final int RETURN_PORTAL_COOLDOWN_TICKS = 60;

    /** Distance between a returning player and the rune echo behind them. */
    private static final double ECHO_DISTANCE = 1.0;

    /** Maximum Manhattan distance to search for a safe position around an obstructed entry point. */
    private static final int SAFE_POSITION_SEARCH_RADIUS = 8;

    /** Nesting depth of teleports done by this class; foreign travel is only guarded while it is zero (US-44). */
    private static int modTravelDepth;

    private ChallengeTravel() {
    }

    /**
     * Moves a player into an Architect's Trials dimension.
     * <p>
     * The current location and game mode are stored as entry point unless one is already stored, which keeps
     * the original entry point when a player moves between points inside the same challenge.
     *
     * @param player          the player
     * @param target          the target level
     * @param position        the target position
     * @param yRot            the target yaw
     * @param xRot            the target pitch
     * @param switchAdventure whether to switch the player to Adventure mode (challenges yes, editor no)
     */
    public static void enter(ServerPlayer player, ServerLevel target, Vec3 position, float yRot, float xRot, boolean switchAdventure) {
        enter(player, target, position, yRot, xRot, switchAdventure ? GameType.ADVENTURE : null);
    }

    /**
     * Moves a player into an Architect's Trials dimension like {@link #enter(ServerPlayer, ServerLevel, Vec3, float, float, boolean)},
     * switching to the given game mode (e.g. Survival for mining rooms, US-31).
     *
     * @param player   the player
     * @param target   the target level
     * @param position the target position
     * @param yRot     the target yaw
     * @param xRot     the target pitch
     * @param gameMode the game mode inside, or {@code null} to keep the current one (editor)
     */
    public static void enter(ServerPlayer player, ServerLevel target, Vec3 position, float yRot, float xRot, @Nullable GameType gameMode) {
        if (!player.hasData(ModAttachments.ENTRY_POINT)) {
            player.setData(ModAttachments.ENTRY_POINT, EntryPoint.of(player));
        }
        teleport(player, target, position, yRot, xRot);
        if (gameMode != null) {
            player.setGameMode(gameMode);
        }
    }

    /**
     * Binds the player's stored entry point to a challenge instance.
     *
     * @param player the player
     * @param ref    the instance reference
     */
    public static void bindInstance(ServerPlayer player, EntryPoint.InstanceRef ref) {
        player.getExistingData(ModAttachments.ENTRY_POINT)
                .ifPresent(entry -> player.setData(ModAttachments.ENTRY_POINT, entry.withInstance(ref)));
    }

    /**
     * Returns a player to their stored entry point, restores their game mode, applies the return portal
     * cooldown, clears the entry point, removes the player from their instance and gives back effects parked by
     * infinite scroll effects.
     * <p>
     * The player comes back facing the opposite direction than when they entered (they step out of the portal
     * they walked into), and a short rune echo of the portal appears behind them.
     * <p>
     * If the stored dimension no longer exists, the player is sent to the world spawn and a warning is
     * logged. If the stored position is obstructed, the nearest safe position is used.
     *
     * @param player the player
     * @return {@code true} if an entry point was stored and the player has been returned
     */
    public static boolean returnToEntryPoint(ServerPlayer player) {
        Optional<EntryPoint> stored = player.getExistingData(ModAttachments.ENTRY_POINT);
        if (stored.isEmpty()) {
            return false;
        }
        EntryPoint entry = stored.get();
        entry.instance().ifPresent(ref -> InstanceManager.leave(player.level().getServer(), ref, player.getUUID()));
        ServerLevel level = player.level().getServer().getLevel(entry.dimension());
        if (level == null) {
            ArchitectsTrials.LOGGER.warn("Entry dimension {} of player {} no longer exists; sending them to world spawn",
                    entry.dimension().identifier(), player.getGameProfile().name());
            asModTravel(() -> player.teleport(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING)));
        } else {
            Vec3 safePosition = findSafePosition(level, player, entry.position());
            float returnYaw = Mth.wrapDegrees(entry.yRot() + 180.0F);
            teleport(player, level, safePosition, returnYaw, entry.xRot());
            Vec3 behind = safePosition.subtract(Vec3.directionFromRotation(0.0F, returnYaw).scale(ECHO_DISTANCE));
            PortalEcho.spawn(level, behind, returnYaw);
        }
        player.setPortalCooldown(RETURN_PORTAL_COOLDOWN_TICKS);
        forgetEntryPoint(player, entry);
        return true;
    }

    /**
     * Handles a player who left an Architect's Trials dimension by a foreign teleport (another mod, a vanilla
     * portal, a command; US-44) like a return without completion, but leaves them where the teleport took them:
     * restores their game mode and effects, removes them from their instance and clears the entry point.
     *
     * @param player the player
     * @return {@code true} if an entry point was stored and has been cleared
     */
    public static boolean abandon(ServerPlayer player) {
        Optional<EntryPoint> stored = player.getExistingData(ModAttachments.ENTRY_POINT);
        if (stored.isEmpty()) {
            ScrollEffectApplication.restore(player);
            return false;
        }
        stored.get().instance().ifPresent(ref -> InstanceManager.leave(player.level().getServer(), ref, player.getUUID()));
        forgetEntryPoint(player, stored.get());
        return true;
    }

    /**
     * Runs a teleport on behalf of this mod: the travel guard of {@link ChallengeTravelEvents} lets it pass, both
     * into and out of challenge and editor dimensions. Every teleport of this class runs through it.
     *
     * @param teleport the teleport to run
     */
    public static void asModTravel(Runnable teleport) {
        modTravelDepth++;
        try {
            teleport.run();
        } finally {
            modTravelDepth--;
        }
    }

    /**
     * Checks whether the current teleport is done by this mod (see {@link #asModTravel(Runnable)}).
     *
     * @return {@code true} while a teleport of this mod runs
     */
    public static boolean isModTravel() {
        return modTravelDepth > 0;
    }

    /**
     * Restores the game mode stored in the entry point, clears it and gives back the effects replaced on entry.
     */
    private static void forgetEntryPoint(ServerPlayer player, EntryPoint entry) {
        player.setGameMode(entry.gameMode());
        player.removeData(ModAttachments.ENTRY_POINT);
        ScrollEffectApplication.restore(player);
    }

    /**
     * Brings a player out of an Architect's Trials dimension: to the stored entry point if present, otherwise
     * to the player's respawn point, falling back to the world spawn.
     *
     * @param player the player
     * @return {@code true} if the player was returned to an entry point, {@code false} if sent to a spawn point
     */
    public static boolean exit(ServerPlayer player) {
        if (returnToEntryPoint(player)) {
            return true;
        }
        asModTravel(() -> player.teleport(player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING)));
        player.setPortalCooldown(RETURN_PORTAL_COOLDOWN_TICKS);
        ScrollEffectApplication.restore(player);
        return false;
    }

    /**
     * Checks whether a dimension belongs to Architect's Trials (challenge themes and the editor).
     *
     * @param dimension the dimension key
     * @return {@code true} if the dimension is managed by this mod
     */
    public static boolean isModDimension(ResourceKey<Level> dimension) {
        return ChallengeThemes.isChallengeDimension(dimension) || EditorDimension.isEditor(dimension);
    }

    /**
     * Teleports a player and resets their fall distance.
     */
    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 position, float yRot, float xRot) {
        asModTravel(() -> player.teleport(new TeleportTransition(level, position, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING)));
        player.resetFallDistance();
    }

    /**
     * Finds a position where the player fits. The preferred position is kept if unobstructed; otherwise the
     * nearest position with free space and solid ground is searched. Falls back to the preferred position.
     *
     * @param level     the level to search in
     * @param player    the player whose standing hitbox is checked
     * @param preferred the preferred position
     * @return a safe position, or {@code preferred} if none was found
     */
    static Vec3 findSafePosition(ServerLevel level, ServerPlayer player, Vec3 preferred) {
        if (fits(level, player, preferred)) {
            return preferred;
        }
        BlockPos origin = BlockPos.containing(preferred);
        for (BlockPos candidate : BlockPos.withinManhattan(origin, SAFE_POSITION_SEARCH_RADIUS)) {
            Vec3 position = Vec3.atBottomCenterOf(candidate);
            BlockPos below = candidate.below();
            boolean hasGround = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            if (hasGround && fits(level, player, position)) {
                return position;
            }
        }
        return preferred;
    }

    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 position) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(position);
        return level.noCollision(player, box);
    }
}

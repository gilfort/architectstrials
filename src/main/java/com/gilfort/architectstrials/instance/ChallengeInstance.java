package com.gilfort.architectstrials.instance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A placed challenge instance. The structure is drawn once on creation; from then on the instance is fixed to
 * that structure, slot and transformation — later entries never re-roll.
 *
 * @param id          the unique instance id
 * @param theme       the theme (dimension) id
 * @param tier        the tier
 * @param structure   the metadata id of the placed structure
 * @param slot        the slot index in the theme dimension
 * @param origin      the placement origin of the structure template
 * @param rotation    the applied rotation
 * @param mirror      the applied mirroring
 * @param spawnPoints the player entry points recorded from the structure's player spawn markers
 * @param exits       the positions of the exit bases recorded from the structure's exit markers
 * @param timeLimit   the total time limit in ticks (for progress displays)
 * @param deadline    the {@link ChallengeClock} value at which the time limit expires
 * @param portalDeadline the clock value until which the entry portal may still let players in, or {@code -1}
 *                    once the portal is closed
 * @param participants the players belonging to the instance (online or offline); they keep it alive
 */
public record ChallengeInstance(
        UUID id,
        Identifier theme,
        int tier,
        Identifier structure,
        int slot,
        BlockPos origin,
        Rotation rotation,
        Mirror mirror,
        List<SpawnPoint> spawnPoints,
        List<BlockPos> exits,
        long timeLimit,
        long deadline,
        long portalDeadline,
        List<UUID> participants
) {

    /** Codec used to persist instances. */
    public static final Codec<ChallengeInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ChallengeInstance::id),
            Identifier.CODEC.fieldOf("theme").forGetter(ChallengeInstance::theme),
            Codec.INT.fieldOf("tier").forGetter(ChallengeInstance::tier),
            Identifier.CODEC.fieldOf("structure").forGetter(ChallengeInstance::structure),
            Codec.INT.fieldOf("slot").forGetter(ChallengeInstance::slot),
            BlockPos.CODEC.fieldOf("origin").forGetter(ChallengeInstance::origin),
            Rotation.CODEC.fieldOf("rotation").forGetter(ChallengeInstance::rotation),
            Mirror.CODEC.fieldOf("mirror").forGetter(ChallengeInstance::mirror),
            SpawnPoint.CODEC.listOf().optionalFieldOf("spawn_points", List.of()).forGetter(ChallengeInstance::spawnPoints),
            BlockPos.CODEC.listOf().optionalFieldOf("exits", List.of()).forGetter(ChallengeInstance::exits),
            Codec.LONG.optionalFieldOf("time_limit", 0L).forGetter(ChallengeInstance::timeLimit),
            Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(ChallengeInstance::deadline),
            Codec.LONG.optionalFieldOf("portal_deadline", -1L).forGetter(ChallengeInstance::portalDeadline),
            UUIDUtil.CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(ChallengeInstance::participants)
    ).apply(instance, ChallengeInstance::new));

    /** Creates an instance, defensively copying the marker-derived lists. */
    public ChallengeInstance {
        spawnPoints = List.copyOf(spawnPoints);
        exits = List.copyOf(exits);
        participants = List.copyOf(new LinkedHashSet<>(participants));
    }

    /**
     * An instance is ready for players once its spawn points are known. Their presence proves the structure
     * has been placed, so portals must only let players enter ready instances.
     *
     * @return {@code true} if at least one spawn point exists
     */
    public boolean ready() {
        return !this.spawnPoints.isEmpty();
    }

    /**
     * Returns a copy of this instance with the given spawn points.
     *
     * @param points the spawn points
     * @return the updated instance
     */
    public ChallengeInstance withSpawnPoints(List<SpawnPoint> points) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, points, this.exits, this.timeLimit, this.deadline, this.portalDeadline, this.participants);
    }

    /**
     * Returns a copy of this instance with the given exits.
     *
     * @param exitPositions the positions of the exit bases
     * @return the updated instance
     */
    public ChallengeInstance withExits(List<BlockPos> exitPositions) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, this.spawnPoints, exitPositions, this.timeLimit, this.deadline, this.portalDeadline, this.participants);
    }

    /**
     * Returns a copy of this instance with the given time limit deadline.
     *
     * @param newDeadline the clock value at which the time limit expires
     * @return the updated instance
     */
    public ChallengeInstance withDeadline(long newDeadline) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, this.spawnPoints, this.exits, this.timeLimit, newDeadline, this.portalDeadline, this.participants);
    }

    /**
     * Returns a copy of this instance with the given portal deadline.
     *
     * @param newPortalDeadline the clock value until which the portal is open, or {@code -1} if closed
     * @return the updated instance
     */
    public ChallengeInstance withPortalDeadline(long newPortalDeadline) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, this.spawnPoints, this.exits, this.timeLimit, this.deadline, newPortalDeadline, this.participants);
    }

    /**
     * Returns a copy of this instance with the given participants.
     *
     * @param newParticipants the participants
     * @return the updated instance
     */
    public ChallengeInstance withParticipants(List<UUID> newParticipants) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, this.spawnPoints, this.exits, this.timeLimit, this.deadline, this.portalDeadline, newParticipants);
    }

    /** @return {@code true} while the entry portal may still let players in */
    public boolean portalOpen() {
        return this.portalDeadline >= 0;
    }

    /**
     * Returns the lifecycle state of this instance.
     *
     * @return forming, active (participants inside), idle (portal open, nobody inside) or cleanup due
     */
    public LifecycleState state() {
        if (!this.ready()) {
            return LifecycleState.FORMING;
        }
        if (!this.participants.isEmpty()) {
            return LifecycleState.ACTIVE;
        }
        return this.portalOpen() ? LifecycleState.IDLE : LifecycleState.CLEANUP_DUE;
    }

    /** Lifecycle states of an instance. */
    public enum LifecycleState {
        /** Structure placed but no spawn points; cannot be entered. */
        FORMING,
        /** At least one participant (online or offline) belongs to the instance. */
        ACTIVE,
        /** Nobody inside, but the portal is still open for (late) entry. */
        IDLE,
        /** Portal closed and nobody inside; the instance is removed and its slot cleared. */
        CLEANUP_DUE
    }
}

package com.gilfort.architectstrials.instance;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A placed challenge instance. The structure is drawn once on creation; from then on the instance is fixed to
 * that structure, slot, transformation and scroll options and effects — later entries never re-roll.
 *
 * @param id             the unique instance id
 * @param theme          the theme (dimension) id
 * @param tier           the tier
 * @param structure      the metadata id of the placed structure
 * @param slot           the slot index in the theme dimension
 * @param origin         the placement origin of the structure template
 * @param rotation       the applied rotation
 * @param mirror         the applied mirroring
 * @param spawnPoints    the player entry points recorded from the structure's player spawn markers
 * @param exits          the positions of the exit bases recorded from the structure's exit markers
 * @param timeLimit      the total time limit in ticks (for progress displays)
 * @param deadline       the {@link ChallengeClock} value at which the time limit expires, or {@link #NOT_STARTED}
 *                       until the first player enters (US-41)
 * @param portalDeadline the clock value until which the entry portal may still let players in, or {@code -1}
 *                       once the portal is closed
 * @param options        the multiplayer options of the scroll that opened the instance
 * @param effects        the effect upgrades of the scroll that opened the instance
 * @param roster         the players of the instance
 * @param requiredMobs   the required mobs that seal the exits requiring them (US-30)
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
        ScrollOptions options,
        ScrollEffects effects,
        InstanceRoster roster,
        RequiredMobs requiredMobs
) {

    /** Value of {@link #deadline()} while nobody has entered yet: the time limit has not started. */
    public static final long NOT_STARTED = -1L;

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
            Codec.LONG.optionalFieldOf("deadline", NOT_STARTED).forGetter(ChallengeInstance::deadline),
            Codec.LONG.optionalFieldOf("portal_deadline", -1L).forGetter(ChallengeInstance::portalDeadline),
            ScrollOptions.CODEC.optionalFieldOf("options", ScrollOptions.DEFAULT).forGetter(ChallengeInstance::options),
            ScrollEffects.CODEC.optionalFieldOf("effects", ScrollEffects.NONE).forGetter(ChallengeInstance::effects),
            Codec.mapPair(InstanceRoster.CODEC.optionalFieldOf("roster", InstanceRoster.EMPTY),
                    RequiredMobs.CODEC.optionalFieldOf("required_mobs", RequiredMobs.NONE))
                    .forGetter(i -> Pair.of(i.roster(), i.requiredMobs()))
    ).apply(instance, (id, theme, tier, structure, slot, origin, rotation, mirror, spawnPoints, exits, timeLimit, deadline,
            portalDeadline, options, effects, tail) -> new ChallengeInstance(id, theme, tier, structure, slot, origin, rotation, mirror,
            spawnPoints, exits, timeLimit, deadline, portalDeadline, options, effects, tail.getFirst(), tail.getSecond())));

    /**
     * Creates an instance without required mobs.
     *
     * @param id             the unique instance id
     * @param theme          the theme id
     * @param tier           the tier
     * @param structure      the metadata id of the placed structure
     * @param slot           the slot index
     * @param origin         the placement origin
     * @param rotation       the applied rotation
     * @param mirror         the applied mirroring
     * @param spawnPoints    the player entry points
     * @param exits          the exit base positions
     * @param timeLimit      the total time limit in ticks
     * @param deadline       the clock value at which the time limit expires
     * @param portalDeadline the clock value until which the portal is open, or {@code -1}
     * @param options        the scroll options
     * @param effects        the scroll effects
     * @param roster         the players
     */
    public ChallengeInstance(UUID id, Identifier theme, int tier, Identifier structure, int slot, BlockPos origin, Rotation rotation,
            Mirror mirror, List<SpawnPoint> spawnPoints, List<BlockPos> exits, long timeLimit, long deadline, long portalDeadline,
            ScrollOptions options, ScrollEffects effects, InstanceRoster roster) {
        this(id, theme, tier, structure, slot, origin, rotation, mirror, spawnPoints, exits, timeLimit, deadline, portalDeadline,
                options, effects, roster, RequiredMobs.NONE);
    }

    /** Creates an instance, defensively copying the marker-derived lists. */
    public ChallengeInstance {
        spawnPoints = List.copyOf(spawnPoints);
        exits = List.copyOf(exits);
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

    /** @return {@code true} once the first player entered and the time limit runs */
    public boolean started() {
        return this.deadline != NOT_STARTED;
    }

    /**
     * Returns the remaining time: until the deadline once started, otherwise the whole time limit.
     *
     * @param now the current {@link ChallengeClock} value
     * @return the remaining time in ticks
     */
    public long remainingTicks(long now) {
        return this.started() ? this.deadline - now : this.timeLimit;
    }

    /** @return the players currently belonging to the instance (online or offline) */
    public List<UUID> participants() {
        return this.roster.participants();
    }

    /**
     * Returns a copy of this instance with the given spawn points.
     *
     * @param points the spawn points
     * @return the updated instance
     */
    public ChallengeInstance withSpawnPoints(List<SpawnPoint> points) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, points, this.exits, this.timeLimit, this.deadline, this.portalDeadline, this.options, this.effects, this.roster, this.requiredMobs);
    }

    /**
     * Returns a copy of this instance with the given exits.
     *
     * @param exitPositions the positions of the exit bases
     * @return the updated instance
     */
    public ChallengeInstance withExits(List<BlockPos> exitPositions) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, this.spawnPoints, exitPositions, this.timeLimit, this.deadline, this.portalDeadline, this.options, this.effects, this.roster, this.requiredMobs);
    }

    /**
     * Returns a copy of this instance with the given time limit deadline.
     *
     * @param newDeadline the clock value at which the time limit expires
     * @return the updated instance
     */
    public ChallengeInstance withDeadline(long newDeadline) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, this.spawnPoints, this.exits, this.timeLimit, newDeadline, this.portalDeadline, this.options, this.effects, this.roster, this.requiredMobs);
    }

    /**
     * Returns a copy of this instance with the given portal deadline.
     *
     * @param newPortalDeadline the clock value until which the portal is open, or {@code -1} if closed
     * @return the updated instance
     */
    public ChallengeInstance withPortalDeadline(long newPortalDeadline) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, this.spawnPoints, this.exits, this.timeLimit, this.deadline, newPortalDeadline, this.options, this.effects, this.roster, this.requiredMobs);
    }

    /**
     * Returns a copy of this instance with the given roster.
     *
     * @param newRoster the roster
     * @return the updated instance
     */
    public ChallengeInstance withRoster(InstanceRoster newRoster) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, this.spawnPoints, this.exits, this.timeLimit, this.deadline, this.portalDeadline, this.options, this.effects, newRoster, this.requiredMobs);
    }

    /**
     * Returns a copy of this instance with the given required mobs.
     *
     * @param mobs the required mobs
     * @return the updated instance
     */
    public ChallengeInstance withRequiredMobs(RequiredMobs mobs) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin, this.rotation,
                this.mirror, this.spawnPoints, this.exits, this.timeLimit, this.deadline, this.portalDeadline, this.options, this.effects,
                this.roster, mobs);
    }

    /**
     * Returns a copy of this instance with the given participants (entrants and completions unchanged).
     *
     * @param newParticipants the participants
     * @return the updated instance
     */
    public ChallengeInstance withParticipants(List<UUID> newParticipants) {
        return this.withRoster(this.roster.withParticipants(newParticipants));
    }

    /** @return {@code true} while the entry portal may still let players in */
    public boolean portalOpen() {
        return this.portalDeadline >= 0;
    }

    /**
     * Checks whether a player may enter through the portal, according to the scroll options.
     *
     * @param player the player
     * @param owner  the player who used the scroll
     * @return the decision
     */
    public Admission admission(UUID player, UUID owner) {
        if (this.roster.completed().contains(player)) {
            return Admission.COMPLETED;
        }
        if (this.roster.entrants().contains(player)) {
            return this.options.allowReentry() ? Admission.ALLOWED : Admission.NO_REENTRY;
        }
        if (this.options.solo()) {
            return player.equals(owner) ? Admission.ALLOWED : Admission.NOT_OWNER;
        }
        if (!this.options.unlimitedPlayers() && this.roster.entrants().size() >= this.options.maxPlayers()) {
            return Admission.FULL;
        }
        return Admission.ALLOWED;
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
        if (!this.participants().isEmpty()) {
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

    /** Result of checking whether a player may enter through the portal. */
    public enum Admission {
        /** The player may enter. */
        ALLOWED,
        /** Solo scroll: only the scroll user may enter. */
        NOT_OWNER,
        /** The maximum number of distinct players has been reached. */
        FULL,
        /** The player was inside before and the scroll does not allow re-entry. */
        NO_REENTRY,
        /** The player already completed this run and can never re-enter. */
        COMPLETED;

        /** @return the lang key of the rejection message */
        public String messageKey() {
            return "message.architectstrials.portal.denied." + this.name().toLowerCase(Locale.ROOT);
        }
    }
}

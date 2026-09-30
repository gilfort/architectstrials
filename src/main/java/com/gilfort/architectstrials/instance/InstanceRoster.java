package com.gilfort.architectstrials.instance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * The players of an instance.
 *
 * @param participants players currently belonging to the instance (online or offline); they keep it alive
 * @param entrants     every distinct player who ever entered; counted against the scroll's {@code max_players}
 * @param completed    players who completed the run via an exit; they can never re-enter
 */
public record InstanceRoster(List<UUID> participants, List<UUID> entrants, List<UUID> completed) {

    /** A roster without players. */
    public static final InstanceRoster EMPTY = new InstanceRoster(List.of(), List.of(), List.of());

    /** Persistent codec. */
    public static final Codec<InstanceRoster> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(InstanceRoster::participants),
            UUIDUtil.CODEC.listOf().optionalFieldOf("entrants", List.of()).forGetter(InstanceRoster::entrants),
            UUIDUtil.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(InstanceRoster::completed)
    ).apply(instance, InstanceRoster::new));

    /** Creates a roster, removing duplicates and defensively copying the lists. */
    public InstanceRoster {
        participants = List.copyOf(new LinkedHashSet<>(participants));
        entrants = List.copyOf(new LinkedHashSet<>(entrants));
        completed = List.copyOf(new LinkedHashSet<>(completed));
    }

    /**
     * Returns a roster with the given participants and unchanged entrants and completions.
     *
     * @param newParticipants the participants
     * @return the updated roster
     */
    public InstanceRoster withParticipants(List<UUID> newParticipants) {
        return new InstanceRoster(newParticipants, this.entrants, this.completed);
    }

    /**
     * Returns a roster in which the player entered: participant and entrant.
     *
     * @param player the player
     * @return the updated roster
     */
    public InstanceRoster entered(UUID player) {
        return new InstanceRoster(plus(this.participants, player), plus(this.entrants, player), this.completed);
    }

    /**
     * Returns a roster in which the player left (no longer a participant, still an entrant).
     *
     * @param player the player
     * @return the updated roster
     */
    public InstanceRoster left(UUID player) {
        List<UUID> remaining = new ArrayList<>(this.participants);
        remaining.remove(player);
        return new InstanceRoster(remaining, this.entrants, this.completed);
    }

    /**
     * Returns a roster in which the player completed the run.
     *
     * @param player the player
     * @return the updated roster
     */
    public InstanceRoster completedBy(UUID player) {
        return new InstanceRoster(this.participants, this.entrants, plus(this.completed, player));
    }

    private static List<UUID> plus(List<UUID> list, UUID player) {
        List<UUID> result = new ArrayList<>(list);
        result.add(player);
        return result;
    }
}

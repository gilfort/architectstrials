package com.gilfort.architectstrials.instance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * The required mobs of an instance (US-30): mobs spawned by Direct Spawn Markers with the "Required" option. Exits
 * that require them stay sealed until all are defeated.
 *
 * @param remaining the UUIDs of the required mobs that are still alive
 * @param total     the number of required mobs the instance started with
 */
public record RequiredMobs(Set<UUID> remaining, int total) {

    /** No required mobs. */
    public static final RequiredMobs NONE = new RequiredMobs(Set.of(), 0);

    /** Persistent codec. */
    public static final Codec<RequiredMobs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().<Set<UUID>>xmap(HashSet::new, List::copyOf).optionalFieldOf("remaining", Set.of()).forGetter(RequiredMobs::remaining),
            Codec.INT.optionalFieldOf("total", 0).forGetter(RequiredMobs::total)
    ).apply(instance, RequiredMobs::new));

    /** Creates the record, defensively copying the set. */
    public RequiredMobs {
        remaining = Set.copyOf(remaining);
    }

    /**
     * Creates the required mobs of a new instance.
     *
     * @param mobs the UUIDs of all required mobs
     * @return the record, all mobs remaining
     */
    public static RequiredMobs of(Set<UUID> mobs) {
        return new RequiredMobs(mobs, mobs.size());
    }

    /** @return {@code true} if the instance has required mobs at all */
    public boolean any() {
        return this.total > 0;
    }

    /** @return {@code true} once every required mob has been defeated */
    public boolean allDefeated() {
        return this.remaining.isEmpty();
    }

    /** @return the number of defeated required mobs */
    public int defeated() {
        return this.total - this.remaining.size();
    }

    /**
     * Returns a copy without the given mob.
     *
     * @param mob the UUID of the defeated mob
     * @return the updated record
     */
    public RequiredMobs without(UUID mob) {
        Set<UUID> rest = new HashSet<>(this.remaining);
        rest.remove(mob);
        return new RequiredMobs(rest, this.total);
    }
}

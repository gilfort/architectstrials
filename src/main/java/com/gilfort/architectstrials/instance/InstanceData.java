package com.gilfort.architectstrials.instance;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent registry of the challenge instances of one theme dimension, stored in that dimension's data
 * folder.
 */
public final class InstanceData extends SavedData {

    /** Saved data type; stored as {@code data/architectstrials/instances.dat} per dimension. */
    public static final SavedDataType<InstanceData> TYPE = new SavedDataType<>(ArchitectsTrials.id("instances"), InstanceData::new,
            ChallengeInstance.CODEC.listOf().xmap(InstanceData::new, data -> List.copyOf(data.instances.values())));

    private final Map<UUID, ChallengeInstance> instances = new LinkedHashMap<>();

    /** Index of the instances by slot; derived from {@link #instances}, not persisted. */
    private final Map<Integer, UUID> bySlot = new HashMap<>();

    /** Creates empty instance data. */
    public InstanceData() {
    }

    private InstanceData(List<ChallengeInstance> instances) {
        instances.forEach(instance -> {
            this.instances.put(instance.id(), instance);
            this.bySlot.put(instance.slot(), instance.id());
        });
    }

    /**
     * Returns all instances in creation order.
     *
     * @return a read-only view of all instances
     */
    public Collection<ChallengeInstance> all() {
        return Collections.unmodifiableCollection(this.instances.values());
    }

    /**
     * Looks up an instance.
     *
     * @param id the instance id
     * @return the instance, or empty if unknown
     */
    public Optional<ChallengeInstance> get(UUID id) {
        return Optional.ofNullable(this.instances.get(id));
    }

    /**
     * Looks up the instance occupying a slot.
     *
     * @param slot the slot index
     * @return the instance, or empty if no instance uses the slot
     */
    public Optional<ChallengeInstance> atSlot(int slot) {
        UUID id = this.bySlot.get(slot);
        return id == null ? Optional.empty() : this.get(id);
    }

    void put(ChallengeInstance instance) {
        this.instances.put(instance.id(), instance);
        this.bySlot.put(instance.slot(), instance.id());
        this.setDirty();
    }

    void remove(UUID id) {
        ChallengeInstance removed = this.instances.remove(id);
        if (removed != null) {
            this.bySlot.remove(removed.slot(), id);
            this.setDirty();
        }
    }
}

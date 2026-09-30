package com.gilfort.architectstrials.block;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A block entity that can reference a loot table, set by builders via {@code /architectstrials marker loot_table}.
 */
public interface LootTableReference {

    /** NBT key the reference is stored under. */
    String TAG = "loot_table_reference";

    /** @return the referenced loot table, if set */
    Optional<ResourceKey<LootTable>> lootTableReference();

    /**
     * Sets or clears the referenced loot table.
     *
     * @param lootTable the loot table, or empty to clear the reference
     */
    void setLootTableReference(Optional<ResourceKey<LootTable>> lootTable);

    /**
     * Reads a stored reference.
     *
     * @param input the value input of the block entity
     * @return the reference, if stored
     */
    static Optional<ResourceKey<LootTable>> read(ValueInput input) {
        return input.read(TAG, ResourceKey.codec(Registries.LOOT_TABLE));
    }

    /**
     * Writes a reference if present.
     *
     * @param output    the value output of the block entity
     * @param lootTable the reference
     */
    static void write(ValueOutput output, Optional<ResourceKey<LootTable>> lootTable) {
        lootTable.ifPresent(key -> output.store(TAG, ResourceKey.codec(Registries.LOOT_TABLE), key));
    }
}

package com.gilfort.architectstrials.loot;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One entry of a {@link LootSetup}: a loot table or a fixed item, a chance and a range of rolls.
 *
 * @param position the position in its group (0–8)
 * @param table    the loot table, if this entry rolls a table
 * @param item     the fixed item, if this entry gives an item (empty otherwise)
 * @param chance   the chance in tenths of a percent (ignored in the consolation list)
 * @param rollsMin the minimum number of rolls
 * @param rollsMax the maximum number of rolls
 */
public record LootEntry(int position, Optional<ResourceKey<LootTable>> table, ItemStack item, int chance, int rollsMin, int rollsMax) {

    /** Largest allowed number of rolls. */
    public static final int MAX_ROLLS = 64;

    /** Persistent codec. */
    public static final Codec<LootEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, LootGroup.MAX_ENTRIES - 1).fieldOf("position").forGetter(LootEntry::position),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("table").forGetter(LootEntry::table),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(LootEntry::item),
            Codec.intRange(0, LootGroup.TOTAL).optionalFieldOf("chance", 0).forGetter(LootEntry::chance),
            Codec.intRange(1, MAX_ROLLS).optionalFieldOf("rolls_min", 1).forGetter(LootEntry::rollsMin),
            Codec.intRange(1, MAX_ROLLS).optionalFieldOf("rolls_max", 1).forGetter(LootEntry::rollsMax)
    ).apply(instance, LootEntry::new));

    /** Normalizes the entry: an item takes precedence over a table, rolls are ordered and clamped. */
    public LootEntry {
        item = item.copy();
        if (!item.isEmpty()) {
            table = Optional.empty();
        }
        chance = Math.clamp(chance, 0, LootGroup.TOTAL);
        rollsMin = Math.clamp(rollsMin, 1, MAX_ROLLS);
        rollsMax = Math.clamp(rollsMax, rollsMin, MAX_ROLLS);
    }

    /**
     * Creates a loot table entry with one roll.
     *
     * @param position the position
     * @param table    the loot table
     * @param chance   the chance in tenths of a percent
     * @return the entry
     */
    public static LootEntry ofTable(int position, ResourceKey<LootTable> table, int chance) {
        return new LootEntry(position, Optional.of(table), ItemStack.EMPTY, chance, 1, 1);
    }

    /**
     * Creates an item entry with one roll.
     *
     * @param position the position
     * @param item     the item
     * @param chance   the chance in tenths of a percent
     * @return the entry
     */
    public static LootEntry ofItem(int position, ItemStack item, int chance) {
        return new LootEntry(position, Optional.empty(), item, chance, 1, 1);
    }

    /** @return {@code true} if the entry neither rolls a table nor gives an item */
    public boolean isEmpty() {
        return this.table.isEmpty() && this.item.isEmpty();
    }

    /**
     * Draws the number of rolls.
     *
     * @param random the random source
     * @return a value between {@link #rollsMin} and {@link #rollsMax}
     */
    public int rolls(RandomSource random) {
        return this.rollsMin + random.nextInt(this.rollsMax - this.rollsMin + 1);
    }

    /**
     * Returns a copy with another chance.
     *
     * @param newChance the chance in tenths of a percent
     * @return the entry
     */
    public LootEntry withChance(int newChance) {
        return new LootEntry(this.position, this.table, this.item, newChance, this.rollsMin, this.rollsMax);
    }

    /**
     * Returns a copy with other rolls.
     *
     * @param min the minimum number of rolls
     * @param max the maximum number of rolls
     * @return the entry
     */
    public LootEntry withRolls(int min, int max) {
        return new LootEntry(this.position, this.table, this.item, this.chance, min, max);
    }
}

package com.gilfort.architectstrials.marker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Weighted random equipment of one equipment slot of a mob marker: up to {@value #MAX_ENTRIES} items, each with a
 * chance in tenths of a percent. At most one item is drawn per roll; the remainder up to 100 % means "nothing" (the
 * slot ends up empty). The chances never sum up to more than 100 %.
 *
 * @param entries the entries, each at its position in the list menu
 */
public record EquipmentList(List<Entry> entries) {

    /** Maximum number of entries. */
    public static final int MAX_ENTRIES = 9;

    /** 100 % in chance units (tenths of a percent). */
    public static final int TOTAL = 1000;

    /** A list without entries. */
    public static final EquipmentList EMPTY = new EquipmentList(List.of());

    /** Persistent codec. */
    public static final Codec<EquipmentList> CODEC = Entry.CODEC.listOf().xmap(EquipmentList::new, EquipmentList::entries);

    /** Creates the list, keeping only valid entries. */
    public EquipmentList {
        entries = entries.stream()
                .filter(entry -> !entry.item().isEmpty() && entry.position() >= 0 && entry.position() < MAX_ENTRIES)
                .toList();
    }

    /**
     * One item of the list.
     *
     * @param position the position in the list menu (0–8)
     * @param item     the item
     * @param chance   the chance in tenths of a percent (0–1000)
     */
    public record Entry(int position, ItemStack item, int chance) {

        /** Persistent codec. */
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, MAX_ENTRIES - 1).fieldOf("position").forGetter(Entry::position),
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(0, TOTAL).fieldOf("chance").forGetter(Entry::chance)
        ).apply(instance, Entry::new));
    }

    /** @return {@code true} if the list holds no item */
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** @return the sum of all chances in tenths of a percent */
    public int totalChance() {
        return this.entries.stream().mapToInt(Entry::chance).sum();
    }

    /**
     * Returns the entry at a position.
     *
     * @param position the position (0–8)
     * @return the entry, if present
     */
    public Optional<Entry> at(int position) {
        return this.entries.stream().filter(entry -> entry.position() == position).findFirst();
    }

    /** @return the item with the highest chance (for previews), or {@link ItemStack#EMPTY} */
    public ItemStack mostLikely() {
        return this.entries.stream().max(Comparator.comparingInt(Entry::chance)).map(Entry::item).orElse(ItemStack.EMPTY);
    }

    /**
     * Returns the list with the entry at a position replaced. An empty item removes the entry.
     *
     * @param position the position (0–8)
     * @param item     the item
     * @param chance   the chance in tenths of a percent; clamped so the list stays at or below 100 %
     * @return the updated list
     */
    public EquipmentList with(int position, ItemStack item, int chance) {
        List<Entry> updated = new ArrayList<>(this.entries.stream().filter(entry -> entry.position() != position).toList());
        if (!item.isEmpty()) {
            int others = updated.stream().mapToInt(Entry::chance).sum();
            updated.add(new Entry(position, item.copy(), Math.clamp(chance, 0, TOTAL - others)));
        }
        updated.sort(Comparator.comparingInt(Entry::position));
        return new EquipmentList(updated);
    }

    /**
     * Draws one item: each entry with its chance, the remainder means "nothing".
     *
     * @param random the random source
     * @return a copy of the drawn item, or {@link ItemStack#EMPTY}
     */
    public ItemStack roll(RandomSource random) {
        int value = random.nextInt(TOTAL);
        for (Entry entry : this.entries) {
            value -= entry.chance();
            if (value < 0) {
                return entry.item().copy();
            }
        }
        return ItemStack.EMPTY;
    }
}

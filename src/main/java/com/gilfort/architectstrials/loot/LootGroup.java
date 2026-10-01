package com.gilfort.architectstrials.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.util.RandomSource;

/**
 * A group of a {@link LootSetup}: up to {@value #MAX_ENTRIES} entries, of which at most one is drawn per roll
 * according to the chances; the remainder up to 100 % means "nothing". Chances never sum up to more than 100 %.
 *
 * @param entries the entries, each at its position
 */
public record LootGroup(List<LootEntry> entries) {

    /** Maximum number of entries per group. */
    public static final int MAX_ENTRIES = 9;

    /** 100 % in chance units (tenths of a percent). */
    public static final int TOTAL = 1000;

    /** A group without entries. */
    public static final LootGroup EMPTY = new LootGroup(List.of());

    /** Persistent codec. */
    public static final Codec<LootGroup> CODEC = LootEntry.CODEC.listOf().xmap(LootGroup::new, LootGroup::entries);

    /** Keeps only non-empty entries, ordered by position. */
    public LootGroup {
        entries = entries.stream().filter(entry -> !entry.isEmpty())
                .sorted(Comparator.comparingInt(LootEntry::position)).toList();
    }

    /** @return {@code true} if the group has no entry */
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * Returns the entry at a position.
     *
     * @param position the position (0–8)
     * @return the entry, if present
     */
    public Optional<LootEntry> at(int position) {
        return this.entries.stream().filter(entry -> entry.position() == position).findFirst();
    }

    /** @return the sum of all chances in tenths of a percent */
    public int totalChance() {
        return this.entries.stream().mapToInt(LootEntry::chance).sum();
    }

    /**
     * Returns the group with the entry at a position replaced (an empty entry removes it). In a chance-based group
     * the entry's chance is clamped so the group stays at or below 100 %.
     *
     * @param entry   the new entry
     * @param clamped {@code true} to clamp the chance (groups), {@code false} for the consolation list
     * @return the updated group
     */
    public LootGroup with(LootEntry entry, boolean clamped) {
        List<LootEntry> updated = new ArrayList<>(this.entries.stream().filter(e -> e.position() != entry.position()).toList());
        if (!entry.isEmpty()) {
            int others = updated.stream().mapToInt(LootEntry::chance).sum();
            updated.add(clamped ? entry.withChance(Math.min(entry.chance(), TOTAL - others)) : entry);
        }
        return new LootGroup(updated);
    }

    /**
     * Returns the group with the entry at a position changed, if it exists.
     *
     * @param position the position
     * @param change   the change
     * @param clamped  whether chances are clamped (see {@link #with})
     * @return the updated group
     */
    public LootGroup update(int position, UnaryOperator<LootEntry> change, boolean clamped) {
        return this.at(position).map(entry -> this.with(change.apply(entry), clamped)).orElse(this);
    }

    /**
     * Draws at most one entry.
     *
     * @param random the random source
     * @return the drawn entry, or empty for "nothing"
     */
    public Optional<LootEntry> draw(RandomSource random) {
        int value = random.nextInt(TOTAL);
        for (LootEntry entry : this.entries) {
            value -= entry.chance();
            if (value < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}

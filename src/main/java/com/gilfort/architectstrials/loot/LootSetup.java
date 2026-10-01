package com.gilfort.architectstrials.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A composed reward, edited with the loot tool (US-25).
 * <ul>
 * <li>Up to {@value #MAX_GROUPS} {@link LootGroup groups}, rolled independently of each other; each draws at most
 * one entry by chance (the remainder means "nothing").</li>
 * <li>An entry rolls a loot table or gives a fixed item, a random number of times within its roll range.</li>
 * <li>The <em>consolation list</em> is given completely (every entry, chances ignored) only if no group drew
 * anything.</li>
 * </ul>
 *
 * @param groups      the groups (exactly {@value #MAX_GROUPS}, possibly empty)
 * @param consolation the consolation list
 */
public record LootSetup(List<LootGroup> groups, LootGroup consolation) {

    /** Number of groups. */
    public static final int MAX_GROUPS = 5;

    /** A setup without any entry. */
    public static final LootSetup EMPTY = new LootSetup(List.of(), LootGroup.EMPTY);

    /** Persistent codec. */
    public static final Codec<LootSetup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LootGroup.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(LootSetup::groups),
            LootGroup.CODEC.optionalFieldOf("consolation", LootGroup.EMPTY).forGetter(LootSetup::consolation)
    ).apply(instance, LootSetup::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, LootSetup> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** Normalizes the setup to exactly {@value #MAX_GROUPS} groups. */
    public LootSetup {
        List<LootGroup> normalized = new ArrayList<>(groups.stream().limit(MAX_GROUPS).toList());
        while (normalized.size() < MAX_GROUPS) {
            normalized.add(LootGroup.EMPTY);
        }
        groups = List.copyOf(normalized);
    }

    /** @return {@code true} if neither a group nor the consolation list has an entry */
    public boolean isEmpty() {
        return this.consolation.isEmpty() && this.groups.stream().allMatch(LootGroup::isEmpty);
    }

    /**
     * Returns a page: groups 0–4, page {@value #MAX_GROUPS} is the consolation list.
     *
     * @param page the page
     * @return the group of that page
     */
    public LootGroup page(int page) {
        return page == MAX_GROUPS ? this.consolation : this.groups.get(page);
    }

    /**
     * Returns the setup with a page replaced.
     *
     * @param page  the page (0–5, 5 = consolation)
     * @param group the new group
     * @return the updated setup
     */
    public LootSetup withPage(int page, LootGroup group) {
        if (page == MAX_GROUPS) {
            return new LootSetup(this.groups, group);
        }
        List<LootGroup> updated = new ArrayList<>(this.groups);
        updated.set(page, group);
        return new LootSetup(updated, this.consolation);
    }

    /**
     * Rolls the setup.
     *
     * @param random     the random source
     * @param rollTable  rolls one loot table once, passing its items to the consumer
     * @param output     receives the resulting items
     */
    public void roll(RandomSource random, BiConsumer<ResourceKey<LootTable>, Consumer<ItemStack>> rollTable, Consumer<ItemStack> output) {
        boolean drawn = false;
        for (LootGroup group : this.groups) {
            Optional<LootEntry> entry = group.draw(random);
            if (entry.isPresent()) {
                drawn = true;
                give(entry.get(), random, rollTable, output);
            }
        }
        if (!drawn) {
            this.consolation.entries().forEach(entry -> give(entry, random, rollTable, output));
        }
    }

    private static void give(LootEntry entry, RandomSource random, BiConsumer<ResourceKey<LootTable>, Consumer<ItemStack>> rollTable,
            Consumer<ItemStack> output) {
        int rolls = entry.rolls(random);
        for (int i = 0; i < rolls; i++) {
            if (entry.table().isPresent()) {
                rollTable.accept(entry.table().get(), output);
            } else {
                output.accept(entry.item().copy());
            }
        }
    }
}

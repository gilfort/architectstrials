package com.gilfort.architectstrials.sub;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

/**
 * What a Sub Structure Marker places (US-32): sub structures with a chance in percent each (sum at most 100 %),
 * and an optional fallback ("consolation prize") placed when none of them is rolled. Without fallback a roll
 * without hit places nothing. Stored in the marker's block entity and copied with the Sub Structure Tool.
 *
 * @param entries  the sub structures with their chances
 * @param fallback the sub structure placed on no hit, if any
 */
public record SubStructureSetup(List<Entry> entries, Optional<Identifier> fallback) {

    /** Maximum number of entries. */
    public static final int MAX_ENTRIES = 6;

    /** Nothing to place. */
    public static final SubStructureSetup EMPTY = new SubStructureSetup(List.of(), Optional.empty());

    /** Persistent codec. */
    public static final Codec<SubStructureSetup> CODEC = RecordCodecBuilder.<SubStructureSetup>create(instance -> instance.group(
            Entry.CODEC.listOf(0, MAX_ENTRIES).optionalFieldOf("entries", List.of()).forGetter(SubStructureSetup::entries),
            Identifier.CODEC.optionalFieldOf("fallback").forGetter(SubStructureSetup::fallback)
    ).apply(instance, SubStructureSetup::new)).validate(SubStructureSetup::validate);

    /** Network codec. */
    public static final StreamCodec<ByteBuf, SubStructureSetup> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), SubStructureSetup::entries,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), SubStructureSetup::fallback,
            SubStructureSetup::new);

    /** Creates the setup, defensively copying the entries. */
    public SubStructureSetup {
        entries = List.copyOf(entries);
    }

    private static DataResult<SubStructureSetup> validate(SubStructureSetup setup) {
        return setup.totalPercent() > 100
                ? DataResult.error(() -> "Sub structure chances add up to more than 100 %: " + setup.totalPercent())
                : DataResult.success(setup);
    }

    /** @return the sum of all chances in percent */
    public int totalPercent() {
        return this.entries.stream().mapToInt(Entry::percent).sum();
    }

    /** @return {@code true} if the setup can never place anything */
    public boolean isEmpty() {
        return this.entries.isEmpty() && this.fallback.isEmpty();
    }

    /**
     * Rolls the sub structure to place.
     *
     * @param random the random source
     * @return the rolled sub structure, the fallback on no hit, or empty
     */
    public Optional<Identifier> roll(RandomSource random) {
        int roll = random.nextInt(100);
        for (Entry entry : this.entries) {
            roll -= entry.percent();
            if (roll < 0) {
                return Optional.of(entry.structure());
            }
        }
        return this.fallback;
    }

    /**
     * One sub structure of a setup.
     *
     * @param structure the sub structure id
     * @param percent   the chance in percent (1–100)
     */
    public record Entry(Identifier structure, int percent) {

        /** Persistent codec. */
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("structure").forGetter(Entry::structure),
                ExtraCodecs.intRange(1, 100).fieldOf("percent").forGetter(Entry::percent)
        ).apply(instance, Entry::new));

        /** Network codec. */
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::structure,
                ByteBufCodecs.VAR_INT, Entry::percent,
                Entry::new);
    }
}

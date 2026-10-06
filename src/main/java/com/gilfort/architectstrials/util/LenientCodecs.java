package com.gilfort.architectstrials.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

/**
 * Codecs that decode collections entry by entry (US-27).
 * <p>
 * Standard list and map codecs fail as a whole if a single entry is invalid, e.g. an item or effect of a removed
 * mod. Data decoded that way is dropped on load and lost for good on the next save. These codecs skip invalid
 * entries with a warning in the log and keep the rest. Encoding is unchanged.
 */
public final class LenientCodecs {

    private LenientCodecs() {
    }

    /**
     * Creates a list codec that skips entries which fail to decode.
     *
     * @param element the codec of one entry
     * @param what    a short description of the data for the log, e.g. {@code "scroll effect"}
     * @param <T>     the entry type
     * @return the lenient list codec
     */
    public static <T> Codec<List<T>> list(Codec<T> element, String what) {
        return Codec.of(element.listOf(), new Decoder<>() {
            @Override
            public <O> DataResult<Pair<List<T>, O>> decode(DynamicOps<O> ops, O input) {
                return ops.getList(input).map(entries -> {
                    List<T> result = new ArrayList<>();
                    entries.accept(entry -> element.parse(ops, entry)
                            .ifSuccess(result::add)
                            .ifError(error -> warn(what, error.message())));
                    return Pair.of(List.copyOf(result), input);
                });
            }
        });
    }

    /**
     * Creates a map codec (keys encoded as strings) that skips entries whose key or value fails to decode.
     *
     * @param key   the key codec
     * @param value the value codec
     * @param what  a short description of the data for the log
     * @param <K>   the key type
     * @param <V>   the value type
     * @return the lenient map codec
     */
    public static <K, V> Codec<Map<K, V>> map(Codec<K> key, Codec<V> value, String what) {
        return Codec.of(Codec.unboundedMap(key, value), new Decoder<>() {
            @Override
            public <O> DataResult<Pair<Map<K, V>, O>> decode(DynamicOps<O> ops, O input) {
                return ops.getMapValues(input).map(entries -> {
                    Map<K, V> result = new LinkedHashMap<>();
                    entries.forEach(entry -> key.parse(ops, entry.getFirst())
                            .ifError(error -> warn(what, error.message()))
                            .ifSuccess(k -> value.parse(ops, entry.getSecond())
                                    .ifSuccess(v -> result.put(k, v))
                                    .ifError(error -> warn(what, error.message()))));
                    return Pair.of(Collections.unmodifiableMap(result), input);
                });
            }
        });
    }

    private static void warn(String what, String message) {
        ArchitectsTrials.LOGGER.warn("Skipped invalid {} entry (missing mod?): {}", what, message);
    }
}

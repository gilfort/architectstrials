package com.gilfort.architectstrials.editor;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * An area selected with the {@link SelectionToolItem}, stored on the tool as data component
 * {@code architectstrials:selection}. Both corners are inclusive and belong to one dimension; setting a corner in
 * another dimension starts a new selection.
 *
 * @param dimension the dimension of the selection
 * @param first     the first corner (left click), if set
 * @param second    the second corner (right click), if set
 */
public record Selection(ResourceKey<Level> dimension, Optional<BlockPos> first, Optional<BlockPos> second) {

    /** Persistent codec. */
    public static final Codec<Selection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Selection::dimension),
            BlockPos.CODEC.optionalFieldOf("first").forGetter(Selection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(Selection::second)
    ).apply(instance, Selection::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Selection> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), Selection::dimension,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::second,
            Selection::new);

    /**
     * Returns the selection with one corner set. A corner in another dimension starts a new selection.
     *
     * @param current   the current selection, or {@code null}
     * @param dimension the dimension of the new corner
     * @param pos       the corner position
     * @param first     {@code true} for the first corner, {@code false} for the second
     * @return the updated selection
     */
    public static Selection withCorner(@Nullable Selection current, ResourceKey<Level> dimension, BlockPos pos, boolean first) {
        Selection base = current != null && current.dimension.equals(dimension)
                ? current : new Selection(dimension, Optional.empty(), Optional.empty());
        return first ? new Selection(dimension, Optional.of(pos.immutable()), base.second)
                : new Selection(dimension, base.first, Optional.of(pos.immutable()));
    }

    /** @return the selected box, if both corners are set */
    public Optional<BoundingBox> box() {
        return this.first.isPresent() && this.second.isPresent()
                ? Optional.of(BoundingBox.fromCorners(this.first.get(), this.second.get())) : Optional.empty();
    }
}

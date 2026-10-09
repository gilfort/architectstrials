package com.gilfort.architectstrials.marker;

import java.util.ArrayList;
import java.util.List;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The complete configuration of a spawn marker (Direct Spawn, Spawner or Trial Spawner Marker) held by the Spawn
 * Marker Tool (US-39). The configuration is the marker's saved block entity data, so every setting (rows, counts,
 * fixed equipment, random lists, equipment loot tables, required flag, trial spawner settings and rewards) is copied
 * without listing it here. It can only be pasted onto a marker of the same block.
 *
 * @param marker the id of the marker block the configuration was copied from
 * @param data   the saved block entity data
 * @param eggs   the spawn eggs of all rows, for the tooltip
 */
public record SpawnMarkerClipboard(Identifier marker, CustomData data, List<ItemStack> eggs) {

    /** Persistent codec. */
    public static final Codec<SpawnMarkerClipboard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("marker").forGetter(SpawnMarkerClipboard::marker),
            CustomData.CODEC.fieldOf("data").forGetter(SpawnMarkerClipboard::data),
            ItemStack.CODEC.listOf().optionalFieldOf("eggs", List.of()).forGetter(SpawnMarkerClipboard::eggs)
    ).apply(instance, SpawnMarkerClipboard::new));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnMarkerClipboard> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SpawnMarkerClipboard::marker,
            CustomData.STREAM_CODEC, SpawnMarkerClipboard::data,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, SpawnMarkerClipboard::eggs,
            SpawnMarkerClipboard::new);

    /**
     * Copies the configuration of a marker.
     *
     * @param marker the marker
     * @return the clipboard content
     */
    public static SpawnMarkerClipboard copy(MobMarkerBlockEntity marker) {
        List<ItemStack> eggs = new ArrayList<>();
        for (int row = 0; row < marker.rows(); row++) {
            if (marker.entityType(row) != null) {
                eggs.add(marker.egg(row).copy());
            }
        }
        return new SpawnMarkerClipboard(BuiltInRegistries.BLOCK.getKey(marker.getBlockState().getBlock()),
                CustomData.of(marker.saveCustomOnly(marker.getLevel().registryAccess())), eggs);
    }

    /**
     * Checks whether the configuration fits a marker (same marker block).
     *
     * @param target the marker
     * @return {@code true} if it can be pasted
     */
    public boolean fits(MobMarkerBlockEntity target) {
        return this.marker.equals(BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()));
    }

    /**
     * Replaces the complete configuration of a marker with this one. The caller checks {@link #fits} first.
     *
     * @param target the marker
     */
    public void pasteInto(MobMarkerBlockEntity target) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(
                () -> "spawn marker clipboard " + target.getBlockPos(), ArchitectsTrials.LOGGER)) {
            target.loadCustomOnly(TagValueInput.create(reporter, target.getLevel().registryAccess(), this.data.copyTag()));
        }
        target.setChanged();
    }

    /** @return the marker block the configuration was copied from */
    public Block markerBlock() {
        return BuiltInRegistries.BLOCK.getValue(this.marker);
    }
}

package com.gilfort.architectstrials.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A possible entry point for players inside an instance, recorded from a player spawn marker.
 *
 * @param pos    the block position the player stands in
 * @param facing the horizontal direction the player looks at after spawning
 */
public record SpawnPoint(BlockPos pos, Direction facing) {

    /** Codec used to persist spawn points as part of an instance. */
    public static final Codec<SpawnPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(SpawnPoint::pos),
            Direction.CODEC.fieldOf("facing").forGetter(SpawnPoint::facing)
    ).apply(instance, SpawnPoint::new));
}

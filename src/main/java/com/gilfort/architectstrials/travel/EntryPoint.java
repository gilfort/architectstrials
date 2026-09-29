package com.gilfort.architectstrials.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The location and game mode a player had right before entering an Architect's Trials dimension.
 * The player is always returned to exactly this point.
 *
 * @param dimension the dimension the player came from
 * @param position  the exact position the player came from
 * @param yRot      the horizontal rotation (yaw)
 * @param xRot      the vertical rotation (pitch)
 * @param gameMode  the game mode to restore on return
 */
public record EntryPoint(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot, GameType gameMode) {

    /** Codec used to persist the entry point as a player attachment. */
    public static final MapCodec<EntryPoint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(EntryPoint::dimension),
            Vec3.CODEC.fieldOf("position").forGetter(EntryPoint::position),
            Codec.FLOAT.fieldOf("y_rot").forGetter(EntryPoint::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(EntryPoint::xRot),
            GameType.CODEC.fieldOf("game_mode").forGetter(EntryPoint::gameMode)
    ).apply(instance, EntryPoint::new));

    /**
     * Captures the current location and game mode of a player.
     *
     * @param player the player
     * @return the entry point describing where the player currently is
     */
    public static EntryPoint of(ServerPlayer player) {
        return new EntryPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer());
    }
}

package com.gilfort.architectstrials.instance;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A placed challenge instance. The structure is drawn once on creation; from then on the instance is fixed to
 * that structure, slot and transformation — later entries never re-roll.
 *
 * @param id          the unique instance id
 * @param theme       the theme (dimension) id
 * @param tier        the tier
 * @param structure   the metadata id of the placed structure
 * @param slot        the slot index in the theme dimension
 * @param origin      the placement origin of the structure template
 * @param rotation    the applied rotation
 * @param mirror      the applied mirroring
 * @param spawnPoints the player entry points recorded from the structure's player spawn markers
 */
public record ChallengeInstance(
        UUID id,
        Identifier theme,
        int tier,
        Identifier structure,
        int slot,
        BlockPos origin,
        Rotation rotation,
        Mirror mirror,
        List<SpawnPoint> spawnPoints
) {

    /** Codec used to persist instances. */
    public static final Codec<ChallengeInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ChallengeInstance::id),
            Identifier.CODEC.fieldOf("theme").forGetter(ChallengeInstance::theme),
            Codec.INT.fieldOf("tier").forGetter(ChallengeInstance::tier),
            Identifier.CODEC.fieldOf("structure").forGetter(ChallengeInstance::structure),
            Codec.INT.fieldOf("slot").forGetter(ChallengeInstance::slot),
            BlockPos.CODEC.fieldOf("origin").forGetter(ChallengeInstance::origin),
            Rotation.CODEC.fieldOf("rotation").forGetter(ChallengeInstance::rotation),
            Mirror.CODEC.fieldOf("mirror").forGetter(ChallengeInstance::mirror),
            SpawnPoint.CODEC.listOf().optionalFieldOf("spawn_points", List.of()).forGetter(ChallengeInstance::spawnPoints)
    ).apply(instance, ChallengeInstance::new));

    /** Creates an instance, defensively copying the spawn points. */
    public ChallengeInstance {
        spawnPoints = List.copyOf(spawnPoints);
    }

    /**
     * An instance is ready for players once its spawn points are known. Their presence proves the structure
     * has been placed, so portals must only let players enter ready instances.
     *
     * @return {@code true} if at least one spawn point exists
     */
    public boolean ready() {
        return !this.spawnPoints.isEmpty();
    }

    /**
     * Returns a copy of this instance with the given spawn points.
     *
     * @param points the spawn points
     * @return the updated instance
     */
    public ChallengeInstance withSpawnPoints(List<SpawnPoint> points) {
        return new ChallengeInstance(this.id, this.theme, this.tier, this.structure, this.slot, this.origin,
                this.rotation, this.mirror, points);
    }
}

package com.gilfort.architectstrials.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Creates challenge instances: allocates a slot, draws a structure once from the theme + tier pool, places it
 * centered in the slot and runs the marker pass.
 */
public final class InstanceManager {

    private InstanceManager() {
    }

    /**
     * Returns the persistent instance registry of a theme level.
     *
     * @param level the theme level
     * @return the instance data, created on first access
     */
    public static InstanceData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(InstanceData.TYPE);
    }

    /**
     * Creates a new instance of a theme and tier.
     * <p>
     * Fails without side effects if the pool is empty, no slot is available, or the drawn structure's
     * template is missing or too large.
     *
     * @param level  the level of the theme dimension
     * @param theme  the theme
     * @param tier   the tier
     * @param random the random source for structure selection and transformation
     * @return the outcome
     */
    public static InstanceCreation create(ServerLevel level, ChallengeTheme theme, int tier, RandomSource random) {
        Optional<Identifier> drawn = ChallengeStructures.draw(theme.id(), tier, random);
        if (drawn.isEmpty()) {
            return new InstanceCreation.Failure(Component.translatable("message.architectstrials.instance.pool_empty",
                    theme.displayName(), tier));
        }
        ChallengeStructure structure = ChallengeStructures.get(drawn.get()).orElseThrow();
        Optional<StructureTemplate> template = level.getServer().getStructureTemplateManager().get(structure.structure());
        if (template.isEmpty()) {
            ArchitectsTrials.LOGGER.error("Structure template {} of challenge structure {} is missing", structure.structure(), drawn.get());
            return new InstanceCreation.Failure(Component.translatable("message.architectstrials.instance.template_missing",
                    structure.structure().toString()));
        }
        Vec3i size = template.get().getSize();
        if (size.getX() > Slot.MAX_STRUCTURE_SIZE || size.getZ() > Slot.MAX_STRUCTURE_SIZE) {
            ArchitectsTrials.LOGGER.error("Challenge structure {} is {}x{} blocks, larger than the maximum of {}", drawn.get(),
                    size.getX(), size.getZ(), Slot.MAX_STRUCTURE_SIZE);
            return new InstanceCreation.Failure(Component.translatable("message.architectstrials.instance.too_large",
                    structure.structure().toString(), Slot.MAX_STRUCTURE_SIZE));
        }
        Optional<Slot> slot = SlotManager.allocate(level);
        if (slot.isEmpty()) {
            return new InstanceCreation.Failure(Component.translatable("message.architectstrials.instance.no_slot"));
        }

        Rotation rotation = structure.rotation() ? Rotation.getRandom(random) : Rotation.NONE;
        Mirror mirror = structure.rotation() ? Mirror.values()[random.nextInt(Mirror.values().length)] : Mirror.NONE;
        BlockPos origin = new BlockPos(slot.get().centerX() - size.getX() / 2,
                ArchitectsTrialsConfig.STRUCTURE_PLACEMENT_Y.getAsInt(),
                slot.get().centerZ() - size.getZ() / 2);
        StructurePlaceSettings settings = placeSettings(size, rotation, mirror);
        ChallengeInstance placed = new ChallengeInstance(UUID.randomUUID(), theme.id(), tier, drawn.get(),
                slot.get().index(), origin, rotation, mirror, List.of());

        template.get().placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
        MarkerContext context = new MarkerContext(level, placed, random);
        int markers = MarkerResolvers.resolveAll(context, template.get(), origin, settings);
        ChallengeInstance instance = placed.withSpawnPoints(context.spawnPoints());
        if (!instance.ready()) {
            ArchitectsTrials.LOGGER.warn("Instance {} of structure {} has no player spawn markers and can never be entered",
                    instance.id(), drawn.get());
        }
        data(level).put(instance);
        ArchitectsTrials.LOGGER.debug("Created instance {} of {} tier {} with structure {} in slot {} ({} markers)",
                instance.id(), theme.id(), tier, drawn.get(), instance.slot(), markers);
        return new InstanceCreation.Success(instance);
    }

    /**
     * Removes an instance and releases its slot for clearing.
     *
     * @param level the theme level of the instance
     * @param id    the instance id
     * @return {@code true} if the instance existed
     */
    public static boolean close(ServerLevel level, UUID id) {
        Optional<ChallengeInstance> instance = data(level).get(id);
        if (instance.isEmpty()) {
            return false;
        }
        data(level).remove(id);
        SlotManager.release(level, instance.get().slot());
        return true;
    }

    /**
     * Moves a player into a ready instance, onto a spawn point chosen independently at random for this player.
     * The entry point is stored and the player switched to Adventure (see {@link ChallengeTravel#enter}).
     *
     * @param player   the player
     * @param level    the theme level of the instance
     * @param instance the instance
     * @return {@code true} if the player entered; {@code false} if the instance is not ready
     */
    public static boolean join(ServerPlayer player, ServerLevel level, ChallengeInstance instance) {
        if (!instance.ready()) {
            return false;
        }
        List<SpawnPoint> points = instance.spawnPoints();
        SpawnPoint point = points.get(player.getRandom().nextInt(points.size()));
        ChallengeTravel.enter(player, level, Vec3.atBottomCenterOf(point.pos()), point.facing().toYRot(), 0.0F, true);
        return true;
    }

    /**
     * Builds the placement settings: rotation and mirroring around the template's horizontal center, so the
     * structure stays centered in its slot.
     *
     * @param size     the unrotated template size
     * @param rotation the rotation
     * @param mirror   the mirroring
     * @return the placement settings
     */
    static StructurePlaceSettings placeSettings(Vec3i size, Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2))
                .setIgnoreEntities(false);
    }
}

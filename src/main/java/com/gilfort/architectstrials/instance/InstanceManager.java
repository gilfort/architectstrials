package com.gilfort.architectstrials.instance;

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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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
        ChallengeInstance instance = new ChallengeInstance(UUID.randomUUID(), theme.id(), tier, drawn.get(),
                slot.get().index(), origin, rotation, mirror);

        template.get().placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
        int markers = MarkerResolvers.resolveAll(new MarkerContext(level, instance, random), template.get(), origin, settings);
        data(level).put(instance);
        ArchitectsTrials.LOGGER.debug("Created instance {} of {} tier {} with structure {} in slot {} ({} markers)",
                instance.id(), theme.id(), tier, drawn.get(), instance.slot(), markers);
        return new InstanceCreation.Success(instance);
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

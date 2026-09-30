package com.gilfort.architectstrials.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.marker.MarkerResolvers;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.gilfort.architectstrials.travel.EntryPoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
 * Creates and manages challenge instances: allocates a slot, draws a structure once from the theme + tier pool,
 * places it centered in the slot, runs the marker pass, and tracks participants, portal and time limit.
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
     * @param timeLimitTicks the time limit; raised to at least the portal open duration
     * @return the outcome
     */
    public static InstanceCreation create(ServerLevel level, ChallengeTheme theme, int tier, RandomSource random, long timeLimitTicks) {
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
        long now = ChallengeClock.now(level.getServer());
        long portalOpenTicks = portalOpenTicks();
        ChallengeInstance placed = new ChallengeInstance(UUID.randomUUID(), theme.id(), tier, drawn.get(),
                slot.get().index(), origin, rotation, mirror, List.of(), List.of(),
                now + Math.max(timeLimitTicks, portalOpenTicks), now + portalOpenTicks, List.of());

        template.get().placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
        MarkerContext context = new MarkerContext(level, placed, random);
        int markers = MarkerResolvers.resolveAll(context, template.get(), origin, settings);
        ChallengeInstance instance = placed.withSpawnPoints(context.spawnPoints()).withExits(context.exits());
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
     * Returns the default time limit from the config.
     *
     * @return the configured default time limit in ticks
     */
    public static long defaultTimeLimitTicks() {
        return ArchitectsTrialsConfig.DEFAULT_TIME_LIMIT_MINUTES.getAsInt() * 60L * ChallengeClock.TICKS_PER_SECOND;
    }

    /**
     * Returns how long an entry portal stays open at most (forming phase plus unused timeout). The time limit of
     * an instance is never shorter than this.
     *
     * @return the maximum portal open duration in ticks
     */
    public static long portalOpenTicks() {
        return ChallengePortal.FORMING_TICKS
                + (long) ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.getAsInt() * ChallengeClock.TICKS_PER_SECOND;
    }

    /**
     * Stores an updated version of an instance.
     *
     * @param level    the theme level
     * @param instance the updated instance
     */
    public static void update(ServerLevel level, ChallengeInstance instance) {
        data(level).put(instance);
    }

    /**
     * Marks the entry portal of an instance as closed (e.g. a solo portal after its player went through).
     *
     * @param level the theme level
     * @param id    the instance id
     */
    public static void closePortal(ServerLevel level, UUID id) {
        data(level).get(id).ifPresent(instance -> update(level, instance.withPortalDeadline(-1L)));
    }

    /**
     * Removes a participant from an instance, e.g. after they returned (exit, death, time limit).
     *
     * @param server the server
     * @param ref    the instance reference
     * @param player the participant's UUID
     */
    public static void leave(MinecraftServer server, EntryPoint.InstanceRef ref, UUID player) {
        ServerLevel level = server.getLevel(ref.dimension());
        if (level == null) {
            return;
        }
        data(level).get(ref.id()).ifPresent(instance -> {
            List<UUID> remaining = new ArrayList<>(instance.participants());
            if (remaining.remove(player)) {
                update(level, instance.withParticipants(remaining));
            }
        });
    }

    /**
     * Checks whether a player still belongs to a running instance (used on login).
     *
     * @param player the player
     * @return {@code true} if the player's entry point references an existing instance they participate in
     */
    public static boolean isParticipant(ServerPlayer player) {
        return player.getExistingData(ModAttachments.ENTRY_POINT).flatMap(EntryPoint::instance).map(ref -> {
            ServerLevel level = player.level().getServer().getLevel(ref.dimension());
            return level != null && data(level).get(ref.id()).map(i -> i.participants().contains(player.getUUID())).orElse(false);
        }).orElse(false);
    }

    /**
     * Finds the instance whose slot contains a position.
     *
     * @param level the theme level
     * @param pos   the position
     * @return the instance, or empty if the position is in no instance's slot
     */
    public static Optional<ChallengeInstance> findAt(ServerLevel level, BlockPos pos) {
        for (ChallengeInstance instance : data(level).all()) {
            if (SlotManager.slot(level, instance.slot()).area(level.getMinY(), level.getMaxY()).isInside(pos)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
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
     * The entry point is stored and bound to the instance, the player is switched to Adventure (see
     * {@link ChallengeTravel#enter}) and becomes a participant.
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
        ChallengeTravel.bindInstance(player, new EntryPoint.InstanceRef(level.dimension(), instance.id()));
        ChallengeInstance current = data(level).get(instance.id()).orElse(instance);
        if (!current.participants().contains(player.getUUID())) {
            List<UUID> participants = new ArrayList<>(current.participants());
            participants.add(player.getUUID());
            update(level, current.withParticipants(participants));
        }
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

package com.gilfort.architectstrials.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.ChallengeExitPortalBlock;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;
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
import net.minecraft.world.level.GameType;
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
     * Creates a new instance of a theme and tier. The instance is registered right away; its structure is placed
     * over the following ticks ({@link InstancePlacements}) and it becomes {@link ChallengeInstance#ready() ready}
     * once placement and marker resolution are done.
     * <p>
     * Fails without side effects if the pool is empty, no slot is available, or the drawn structure's
     * template is missing or too large.
     *
     * @param level  the level of the theme dimension
     * @param theme  the theme
     * @param tier   the tier
     * @param random the random source for structure selection and transformation
     * @param timeLimitTicks the time limit; raised to at least the portal open duration
     * @param options the multiplayer options of the scroll, fixed into the instance
     * @return the outcome
     */
    public static InstanceCreation create(ServerLevel level, ChallengeTheme theme, int tier, RandomSource random,
            long timeLimitTicks, ScrollOptions options) {
        return create(level, theme, tier, random, timeLimitTicks, options, ScrollEffects.NONE);
    }

    /**
     * Creates an instance like {@link #create(ServerLevel, ChallengeTheme, int, RandomSource, long, ScrollOptions)},
     * with the effect upgrades of the scroll.
     *
     * @param level          the level of the theme dimension
     * @param theme          the theme
     * @param tier           the tier
     * @param random         the random source for structure selection and transformation
     * @param timeLimitTicks the time limit; raised to at least the portal open duration
     * @param options        the multiplayer options of the scroll, fixed into the instance
     * @param effects        the effect upgrades of the scroll, fixed into the instance
     * @return the outcome
     */
    public static InstanceCreation create(ServerLevel level, ChallengeTheme theme, int tier, RandomSource random,
            long timeLimitTicks, ScrollOptions options, ScrollEffects effects) {
        Optional<Identifier> drawn = ChallengeStructures.drawEnterable(level.getServer(), theme.id(), tier, random);
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
        long portalOpenTicks = portalOpenTicks(options, timeLimitTicks);
        long timeLimit = Math.max(timeLimitTicks, portalOpenTicks);
        ChallengeInstance instance = new ChallengeInstance(UUID.randomUUID(), theme.id(), tier, drawn.get(),
                slot.get().index(), origin, rotation, mirror, List.of(), List.of(),
                timeLimit, now + timeLimit, now + portalOpenTicks, options, effects, InstanceRoster.EMPTY);
        data(level).put(instance);
        ArchitectsTrials.LOGGER.debug("Created instance {} of {} tier {} with structure {} in slot {}; placing",
                instance.id(), theme.id(), tier, drawn.get(), instance.slot());
        InstancePlacements.start(level, new PlacementTask(level, instance.id(), template.get(), origin, settings, random));
        return data(level).get(instance.id())
                .<InstanceCreation>map(InstanceCreation.Success::new)
                .orElseGet(() -> new InstanceCreation.Failure(Component.translatable("message.architectstrials.instance.no_spawn",
                        structure.structure().toString())));
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
     * Returns how long an active entry portal stays open according to the scroll options: the configured
     * unused timeout for {@code portal_open_seconds = 0} (it closes earlier, on the first pass-through), the
     * given seconds for positive values, or the whole time limit for {@code -1}.
     *
     * @param options        the scroll options
     * @param timeLimitTicks the time limit of the instance
     * @return the open duration in ticks, counted from the moment the portal becomes active
     */
    public static long activePortalTicks(ScrollOptions options, long timeLimitTicks) {
        if (options.portalOpenSeconds() == ScrollOptions.OPEN_UNTIL_TIME_LIMIT) {
            return timeLimitTicks;
        }
        int seconds = options.portalOpenSeconds() == 0 ? ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.getAsInt() : options.portalOpenSeconds();
        return (long) seconds * ChallengeClock.TICKS_PER_SECOND;
    }

    /**
     * Returns how long an entry portal exists at most (forming phase plus open duration). The time limit of an
     * instance is never shorter than this.
     *
     * @param options        the scroll options
     * @param timeLimitTicks the requested time limit
     * @return the maximum portal lifetime in ticks
     */
    public static long portalOpenTicks(ScrollOptions options, long timeLimitTicks) {
        return ChallengePortal.FORMING_TICKS + activePortalTicks(options, timeLimitTicks);
    }

    /**
     * Records that a player completed the run of an instance; they can never re-enter it.
     *
     * @param level  the theme level
     * @param id     the instance id
     * @param player the player's UUID
     */
    public static void markCompleted(ServerLevel level, UUID id, UUID player) {
        data(level).get(id).ifPresent(instance -> update(level, instance.withRoster(instance.roster().completedBy(player))));
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
            if (instance.participants().contains(player)) {
                update(level, instance.withRoster(instance.roster().left(player)));
            }
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) {
                InstanceTimerBars.hideFrom(ref.id(), online);
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
     * Finds the instance whose slot contains a position. The slot is computed from the position, so the cost does
     * not depend on the number of instances.
     *
     * @param level the theme level
     * @param pos   the position
     * @return the instance, or empty if the position is in no instance's slot
     */
    public static Optional<ChallengeInstance> findAt(ServerLevel level, BlockPos pos) {
        int index = SlotManager.indexAt(level, pos.getX(), pos.getZ());
        return index < 0 ? Optional.empty() : data(level).atSlot(index);
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
        InstanceTimerBars.remove(id);
        SlotManager.release(level, instance.get().slot());
        return true;
    }

    /**
     * Moves a player into a ready instance, onto a spawn point chosen independently at random for this player.
     * The entry point is stored and bound to the instance, the player is switched to the structure's game mode
     * (Adventure by default, Survival for mining rooms) and becomes a participant and entrant. Admission rules (scroll options) are
     * checked by the portal, not here.
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
        GameType gameMode = ChallengeStructures.get(instance.structure()).map(ChallengeStructure::gameMode).orElse(GameType.ADVENTURE);
        ChallengeTravel.enter(player, level, Vec3.atBottomCenterOf(point.pos()), point.facing().toYRot(), 0.0F, gameMode);
        ChallengeTravel.bindInstance(player, new EntryPoint.InstanceRef(level.dimension(), instance.id()));
        ChallengeInstance current = data(level).get(instance.id()).orElse(instance);
        update(level, current.withRoster(current.roster().entered(player.getUUID())));
        ensureExitPortals(level, current);
        ScrollEffectApplication.onEntry(player, level, current);
        return true;
    }

    /**
     * Fills the portal space of an instance's exits with portal blocks if they are missing, e.g. for instances
     * placed before exits used portal blocks (US-38). Only air is replaced, so this is a no-op for current
     * instances.
     *
     * @param level    the theme level
     * @param instance the instance
     */
    public static void ensureExitPortals(ServerLevel level, ChallengeInstance instance) {
        if (!instance.exits().isEmpty()
                && !(level.getBlockState(instance.exits().getFirst().above()).getBlock() instanceof ChallengeExitPortalBlock)) {
            ChallengeExitPortalBlock.fill(level, instance.exits());
        }
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

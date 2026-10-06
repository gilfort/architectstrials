package com.gilfort.architectstrials.portal;

import java.util.Optional;
import java.util.UUID;

import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModEntityTypes;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A challenge portal opened by a scroll. It is not a block but a particle-only entity (1×2 blocks) that may
 * clip into blocks.
 * <p>
 * Lifecycle: <em>forming</em> (particles, not enterable) → <em>active</em> once its instance is ready →
 * closed. Who may enter and how long the portal stays open is decided by the instance's {@link ScrollOptions}
 * (solo default: only the scroll user, closing after their first pass-through). If nobody ever enters before
 * the portal's time is up, it collapses, the instance is cleaned up and the scroll drops again with the
 * configured chance.
 */
public class ChallengePortal extends Entity {

    /** Minimum number of ticks a portal stays in the forming state, for the visual transition. */
    public static final int FORMING_TICKS = 30;

    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(ChallengePortal.class, EntityDataSerializers.BOOLEAN);
    private static final int TICKS_PER_SECOND = 20;

    private UUID owner = new UUID(0L, 0L);
    private UUID instanceId = new UUID(0L, 0L);
    private ResourceKey<Level> themeDimension = Level.OVERWORLD;
    private ItemStack scroll = ItemStack.EMPTY;
    private int activeSince = -1;
    private int age;

    /**
     * Creates a portal entity; used by the entity type factory.
     *
     * @param type  the entity type
     * @param level the level
     */
    public ChallengePortal(EntityType<? extends ChallengePortal> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Opens a new portal and adds it to the level.
     *
     * @param level    the level to open the portal in
     * @param pos      the lower block position of the portal
     * @param yaw      the yaw the portal faces
     * @param owner    the player who used the scroll
     * @param instance the instance behind the portal
     * @param scroll   a single copy of the used scroll, dropped again with a chance if the portal expires
     * @return the portal
     */
    public static ChallengePortal open(ServerLevel level, BlockPos pos, float yaw, ServerPlayer owner, ChallengeInstance instance, ItemStack scroll) {
        ChallengePortal portal = new ChallengePortal(ModEntityTypes.CHALLENGE_PORTAL.get(), level);
        portal.snapTo(Vec3.atBottomCenterOf(pos), yaw, 0.0F);
        portal.owner = owner.getUUID();
        portal.instanceId = instance.id();
        portal.themeDimension = ResourceKey.create(Registries.DIMENSION, instance.theme());
        portal.scroll = scroll;
        level.addFreshEntity(portal);
        level.playSound(null, portal.getX(), portal.getY(), portal.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6F, 1.4F);
        return portal;
    }

    /** @return {@code true} once the portal can be entered */
    public boolean isActive() {
        return this.entityData.get(DATA_ACTIVE);
    }

    /** @return the id of the instance behind this portal */
    public UUID instanceId() {
        return this.instanceId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ACTIVE, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        } else {
            this.spawnParticles();
        }
    }

    private void serverTick(ServerLevel level) {
        this.age++;
        ServerLevel themeLevel = level.getServer().getLevel(this.themeDimension);
        Optional<ChallengeInstance> instance = themeLevel == null ? Optional.empty() : InstanceManager.data(themeLevel).get(this.instanceId);
        if (instance.isEmpty()) {
            this.discard();
            return;
        }
        if (!this.isActive()) {
            if (instance.get().ready() && this.age >= FORMING_TICKS) {
                this.entityData.set(DATA_ACTIVE, true);
                this.activeSince = this.age;
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            return;
        }
        ChallengeInstance current = instance.get();
        if (this.age - this.activeSince > InstanceManager.activePortalTicks(current.options(), current.timeLimit())) {
            if (current.roster().entrants().isEmpty()) {
                this.expire(level, themeLevel);
            } else {
                this.close(themeLevel);
            }
        }
    }

    /**
     * Lets a player touching the active portal enter its instance, if the scroll options admit them. Vanilla calls
     * this from the player's own tick for nearby entities, so the portal needs no entity query of its own.
     *
     * @param player the touching player
     */
    @Override
    public void playerTouch(Player player) {
        if (!(this.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer) || !this.isActive()
                || this.isRemoved() || !canUsePortals(serverPlayer) || !serverPlayer.getBoundingBox().intersects(this.getBoundingBox())) {
            return;
        }
        ServerLevel themeLevel = level.getServer().getLevel(this.themeDimension);
        Optional<ChallengeInstance> instance = themeLevel == null ? Optional.empty() : InstanceManager.data(themeLevel).get(this.instanceId);
        if (instance.isEmpty()) {
            return;
        }
        ChallengeInstance.Admission admission = instance.get().admission(serverPlayer.getUUID(), this.owner);
        if (admission != ChallengeInstance.Admission.ALLOWED) {
            if (this.age % TICKS_PER_SECOND == 0) {
                serverPlayer.sendOverlayMessage(Component.translatable(admission.messageKey()));
            }
            return;
        }
        if (InstanceManager.join(serverPlayer, themeLevel, instance.get())) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.4F, 1.6F);
            if (this.closesAfterEntry(InstanceManager.data(themeLevel).get(this.instanceId).orElse(instance.get()))) {
                this.close(themeLevel);
            }
        }
    }

    /**
     * Players may use portals unless dead, spectating or on portal cooldown (e.g. right after returning).
     */
    private static boolean canUsePortals(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isOnPortalCooldown();
    }

    /**
     * Decides whether the portal closes right after a player entered: always for {@code portal_open_seconds = 0},
     * and once {@code max_players} is reached unless re-entry must remain possible.
     */
    private boolean closesAfterEntry(ChallengeInstance instance) {
        ScrollOptions options = instance.options();
        if (options.portalOpenSeconds() == 0) {
            return true;
        }
        boolean full = !options.unlimitedPlayers() && instance.roster().entrants().size() >= options.maxPlayers();
        return full && !options.allowReentry();
    }

    /**
     * Closes a used portal: the instance keeps running for its participants.
     */
    private void close(ServerLevel themeLevel) {
        InstanceManager.closePortal(themeLevel, this.instanceId);
        this.discard();
    }

    /**
     * Closes an unused portal: cleans up the instance and drops the scroll again with the configured
     * {@link ArchitectsTrialsConfig#UNUSED_PORTAL_SCROLL_DROP_CHANCE}.
     */
    private void expire(ServerLevel level, ServerLevel themeLevel) {
        InstanceManager.close(themeLevel, this.instanceId);
        if (!this.scroll.isEmpty() && this.random.nextDouble() < ArchitectsTrialsConfig.UNUSED_PORTAL_SCROLL_DROP_CHANCE.getAsDouble()) {
            this.spawnAtLocation(level, this.scroll.copy());
        }
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(this.owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.translatable("message.architectstrials.portal.expired"));
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
        this.discard();
    }

    /**
     * Client-side particles: rune glyphs while forming; once active only a few accents, the surface itself is
     * drawn by the entity renderer.
     */
    private void spawnParticles() {
        if (this.isActive()) {
            if (this.random.nextInt(3) == 0) {
                PortalParticles.swirl(this, 1);
            }
        } else {
            PortalParticles.runes(this, 2);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(this.owner);
        this.instanceId = input.read("instance", UUIDUtil.CODEC).orElse(this.instanceId);
        this.themeDimension = input.read("theme_dimension", ResourceKey.codec(Registries.DIMENSION)).orElse(this.themeDimension);
        this.scroll = input.read("scroll", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.activeSince = input.getIntOr("active_since", -1);
        this.age = input.getIntOr("age", 0);
        this.entityData.set(DATA_ACTIVE, this.activeSince >= 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("owner", UUIDUtil.CODEC, this.owner);
        output.store("instance", UUIDUtil.CODEC, this.instanceId);
        output.store("theme_dimension", ResourceKey.codec(Registries.DIMENSION), this.themeDimension);
        output.store("scroll", ItemStack.OPTIONAL_CODEC, this.scroll);
        output.putInt("active_since", this.activeSince);
        output.putInt("age", this.age);
    }
}

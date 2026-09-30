package com.gilfort.architectstrials.portal;

import com.gilfort.architectstrials.registry.ModEntityTypes;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Purely visual echo of a portal: shows the rune glyphs of a forming portal for a short moment behind a
 * player returning from a challenge, so they do not simply pop into existence. Never saved, never interactive.
 */
public class PortalEcho extends Entity {

    /** How long the echo stays visible, in ticks (the forming half of the portal animation). */
    public static final int DURATION_TICKS = ChallengePortal.FORMING_TICKS;

    private int age;

    /**
     * Creates an echo entity; used by the entity type factory.
     *
     * @param type  the entity type
     * @param level the level
     */
    public PortalEcho(EntityType<? extends PortalEcho> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Spawns an echo at the given position.
     *
     * @param level    the level
     * @param position the bottom center of the echo
     * @param yaw      the orientation of the echo plane
     * @return the echo
     */
    public static PortalEcho spawn(ServerLevel level, Vec3 position, float yaw) {
        PortalEcho echo = new PortalEcho(ModEntityTypes.PORTAL_ECHO.get(), level);
        echo.snapTo(position, yaw, 0.0F);
        level.addFreshEntity(echo);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 1.8F);
        return echo;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.level().isClientSide()) {
            PortalParticles.runes(this, 4);
        } else if (this.age > DURATION_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

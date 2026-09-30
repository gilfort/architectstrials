package com.gilfort.architectstrials.portal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Client-side particle shapes shared by all portal visuals: an upright 1×2 oval whose plane is described by a
 * yaw angle, standing on a bottom center position.
 */
public final class PortalParticles {

    private PortalParticles() {
    }

    /**
     * Spawns the rune glyphs of a forming portal around a portal-like entity.
     *
     * @param entity the entity (position = bottom center, yaw = orientation)
     * @param count  the number of particles
     */
    static void runes(Entity entity, int count) {
        runes(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getRandom(), count);
    }

    /**
     * Spawns the swirl of an active portal around a portal-like entity.
     *
     * @param entity the entity (position = bottom center, yaw = orientation)
     * @param count  the number of particles
     */
    static void swirl(Entity entity, int count) {
        swirl(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getRandom(), count);
    }

    /**
     * Spawns the rune glyphs of a forming portal.
     *
     * @param level  the client level
     * @param x      bottom center X
     * @param y      bottom Y
     * @param z      bottom center Z
     * @param yaw    orientation of the portal plane
     * @param random the random source
     * @param count  the number of particles
     */
    public static void runes(Level level, double x, double y, double z, float yaw, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            double[] point = ovalPoint(x, y, z, yaw, random);
            level.addParticle(ParticleTypes.ENCHANT, point[0], point[1], point[2], 0.0, 0.05, 0.0);
        }
    }

    /**
     * Spawns the swirl of an active portal.
     *
     * @param level  the client level
     * @param x      bottom center X
     * @param y      bottom Y
     * @param z      bottom center Z
     * @param yaw    orientation of the portal plane
     * @param random the random source
     * @param count  the number of particles
     */
    public static void swirl(Level level, double x, double y, double z, float yaw, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            double[] point = ovalPoint(x, y, z, yaw, random);
            level.addParticle(ParticleTypes.PORTAL, point[0], point[1], point[2],
                    (random.nextDouble() - 0.5) * 0.3, -0.1, (random.nextDouble() - 0.5) * 0.3);
        }
    }

    private static double[] ovalPoint(double x, double y, double z, float yaw, RandomSource random) {
        double yawRadians = Math.toRadians(yaw);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.cos(angle) * 0.5;
        double vertical = 1.0 + Math.sin(angle);
        return new double[] {x + Math.cos(yawRadians) * horizontal, y + vertical, z + Math.sin(yawRadians) * horizontal};
    }
}

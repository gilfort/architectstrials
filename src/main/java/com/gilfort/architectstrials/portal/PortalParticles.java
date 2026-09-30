package com.gilfort.architectstrials.portal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Client-side particle shapes shared by all portal visuals: an upright 1×2 oval in the plane described by the
 * entity's yaw.
 */
final class PortalParticles {

    private PortalParticles() {
    }

    /**
     * Spawns the rune glyphs of a forming portal.
     *
     * @param entity the portal-like entity (position = bottom center, yaw = orientation)
     * @param count  the number of particles to spawn
     */
    static void runes(Entity entity, int count) {
        RandomSource random = entity.getRandom();
        for (int i = 0; i < count; i++) {
            double[] point = ovalPoint(entity, random);
            entity.level().addParticle(ParticleTypes.ENCHANT, point[0], point[1], point[2], 0.0, 0.05, 0.0);
        }
    }

    /**
     * Spawns the swirl of an active portal.
     *
     * @param entity the portal-like entity (position = bottom center, yaw = orientation)
     * @param count  the number of particles to spawn
     */
    static void swirl(Entity entity, int count) {
        RandomSource random = entity.getRandom();
        for (int i = 0; i < count; i++) {
            double[] point = ovalPoint(entity, random);
            entity.level().addParticle(ParticleTypes.PORTAL, point[0], point[1], point[2],
                    (random.nextDouble() - 0.5) * 0.3, -0.1, (random.nextDouble() - 0.5) * 0.3);
        }
    }

    private static double[] ovalPoint(Entity entity, RandomSource random) {
        double yawRadians = Math.toRadians(entity.getYRot());
        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.cos(angle) * 0.5;
        double vertical = 1.0 + Math.sin(angle);
        return new double[] {
                entity.getX() + Math.cos(yawRadians) * horizontal,
                entity.getY() + vertical,
                entity.getZ() + Math.sin(yawRadians) * horizontal};
    }
}

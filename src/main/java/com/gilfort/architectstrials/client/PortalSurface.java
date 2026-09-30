package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Draws the animated, translucent surface shared by entry portals and challenge exits: a double-sided quad
 * whose texture cycles through the frames of a vertical sprite sheet. Replaces dense particle clouds, which
 * would cost far more performance.
 */
final class PortalSurface {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/entity/portal_surface.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(TEXTURE);

    /** Number of animation frames stacked vertically in the texture. */
    private static final int FRAMES = 16;

    /** Game ticks each frame is shown. */
    private static final float TICKS_PER_FRAME = 2.0F;

    private static final int ALPHA = 200;

    private PortalSurface() {
    }

    /**
     * Submits a portal surface standing on the current origin of the pose stack.
     *
     * @param poseStack the pose stack; origin = bottom center of the surface
     * @param collector the submit node collector
     * @param yaw       the yaw of the portal (its plane is perpendicular to this direction)
     * @param width     the width in blocks
     * @param height    the height in blocks
     * @param ageTicks  the animation time in ticks
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, float yaw, float width, float height, float ageTicks) {
        int frame = (int) (ageTicks / TICKS_PER_FRAME) % FRAMES;
        float v0 = (float) frame / FRAMES;
        float v1 = (float) (frame + 1) / FRAMES;
        float halfWidth = width / 2.0F;
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(-yaw));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            quad(buffer, pose, -halfWidth, halfWidth, height, v0, v1);
            quad(buffer, pose, halfWidth, -halfWidth, height, v0, v1);
        });
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float x1, float height, float v0, float v1) {
        vertex(buffer, pose, x0, 0.0F, 0.0F, v1);
        vertex(buffer, pose, x1, 0.0F, 1.0F, v1);
        vertex(buffer, pose, x1, height, 1.0F, v0);
        vertex(buffer, pose, x0, height, 0.0F, v0);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}

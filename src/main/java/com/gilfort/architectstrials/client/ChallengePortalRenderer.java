package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.portal.ChallengePortal;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Renders the animated 1×2 surface of an active entry portal. While forming, only its rune particles show.
 */
public class ChallengePortalRenderer extends EntityRenderer<ChallengePortal, ChallengePortalRenderer.State> {

    private static final float WIDTH = 1.0F;
    private static final float HEIGHT = 2.0F;

    /**
     * Creates the renderer.
     *
     * @param context the renderer context
     */
    public ChallengePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChallengePortal entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.active = entity.isActive();
        state.portalYaw = entity.getYRot();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.active) {
            PortalSurface.submit(poseStack, collector, state.portalYaw, WIDTH, HEIGHT, state.ageInTicks);
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** Render state of an entry portal. */
    public static class State extends EntityRenderState {
        boolean active;
        float portalYaw;
    }
}

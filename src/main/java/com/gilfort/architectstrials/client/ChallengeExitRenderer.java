package com.gilfort.architectstrials.client;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.block.ChallengeExitBlockEntity;
import com.gilfort.architectstrials.block.ExitGroup;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the portal surface above an open {@link ExitGroup}. Only the group's anchor renders, so a group of
 * {@code n} bases shows one {@code n × (n + 1)} surface.
 */
public class ChallengeExitRenderer implements BlockEntityRenderer<ChallengeExitBlockEntity, ChallengeExitRenderer.State> {

    /**
     * Creates the renderer.
     *
     * @param context the renderer context
     */
    public ChallengeExitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChallengeExitBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.visible = false;
        if (blockEntity.getLevel() == null) {
            return;
        }
        ExitGroup.find(blockEntity.getLevel(), blockEntity.getBlockPos())
                .filter(group -> !group.locked() && group.anchor().equals(blockEntity.getBlockPos()))
                .ifPresent(group -> {
                    state.visible = true;
                    state.width = group.width();
                    state.height = group.height();
                    state.facing = group.facing();
                    state.ageTicks = blockEntity.getLevel().getGameTime() + partialTicks;
                });
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        Direction along = ExitGroup.along(state.facing);
        float offset = (state.width - 1) / 2.0F;
        poseStack.pushPose();
        poseStack.translate(0.5F + along.getStepX() * offset, 1.0F, 0.5F + along.getStepZ() * offset);
        PortalSurface.submit(poseStack, collector, state.facing.toYRot(), state.width, state.height, state.ageTicks);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ChallengeExitBlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) {
            return new AABB(blockEntity.getBlockPos());
        }
        return ExitGroup.find(blockEntity.getLevel(), blockEntity.getBlockPos())
                .map(group -> group.portalArea().minmax(new AABB(group.anchor())))
                .orElseGet(() -> new AABB(blockEntity.getBlockPos()));
    }

    /** Render state of a challenge exit. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        int width;
        int height;
        Direction facing = Direction.NORTH;
        float ageTicks;
    }
}

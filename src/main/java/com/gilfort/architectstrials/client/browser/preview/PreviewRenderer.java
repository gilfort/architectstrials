package com.gilfort.architectstrials.client.browser.preview;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders a {@link StructureMesh} once into an offscreen texture with a fixed isometric camera and reads the result
 * back as an image (US-43). Must run on the render thread outside of a frame's world or GUI rendering (the browser
 * calls it from its tick).
 */
public final class PreviewRenderer {

    /** Width of a preview image in pixels. */
    public static final int WIDTH = 1024;
    /** Height of a preview image in pixels. */
    public static final int HEIGHT = 640;

    /** Downward tilt of the camera in degrees. */
    private static final float PITCH = 30.0F;
    /** Part of the image the structure fills at most. */
    private static final float FILL = 0.92F;

    private PreviewRenderer() {
    }

    /**
     * The camera rotation of a viewing direction: the structure is turned around its vertical center, then tilted.
     *
     * @param direction the camera position: 0 = south-east, 1 = north-east, 2 = north-west, 3 = south-west (north = −Z)
     * @return the rotation
     */
    static Matrix4f rotation(int direction) {
        return new Matrix4f().rotateX((float) Math.toRadians(PITCH)).rotateY((float) Math.toRadians(-45.0 - 90.0 * direction));
    }

    /**
     * Renders a mesh and hands the image to a callback once the GPU read-back has finished.
     *
     * @param mesh      the mesh
     * @param direction the viewing direction (0–3)
     * @param onDone    receives the image (the callback owns it)
     */
    public static void render(StructureMesh mesh, int direction, Consumer<NativeImage> onDone) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture color = device.createTexture("Architect's Trials preview", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, WIDTH, HEIGHT, 1, 1);
        GpuTextureView colorView = device.createTextureView(color);
        GpuFormat depthFormat = minecraft.gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
        GpuTexture depth = device.createTexture("Architect's Trials preview depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
                depthFormat, WIDTH, HEIGHT, 1, 1);
        GpuTextureView depthView = device.createTextureView(depth);
        Projection projection = new Projection();
        ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Architect's Trials preview");
        SubmitNodeStorage submits = new SubmitNodeStorage();
        try {
            device.createCommandEncoder().clearColorAndDepthTextures(color, new Vector4f(0.0F, 0.0F, 0.0F, 0.0F), depth, 0.0);
            projection.setupOrtho(-1000.0F, 1000.0F, WIDTH, HEIGHT, true);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            try {
                submit(mesh, direction, submits);
                FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.featureRenderDispatcher();
                try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submits);
                     RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Architect's Trials preview", colorView,
                             Optional.empty(), depthView, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
            } finally {
                modelView.popMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
            readBack(device, color, onDone);
        } finally {
            colorView.close();
            depthView.close();
            depth.close();
            projectionBuffer.close();
        }
    }

    /** Submits the mesh's quads, one custom geometry per render layer, fitted into the image. */
    private static void submit(StructureMesh mesh, int direction, SubmitNodeStorage submits) {
        Vec3i size = mesh.size();
        Matrix4f rotation = rotation(direction);
        float[] bounds = projectedBounds(size, rotation);
        float width = Math.max(1.0E-3F, bounds[1] - bounds[0]);
        float height = Math.max(1.0E-3F, bounds[3] - bounds[2]);
        float scale = Math.min(WIDTH / width, HEIGHT / height) * FILL;
        float centerX = (bounds[0] + bounds[1]) / 2.0F;
        float centerY = (bounds[2] + bounds[3]) / 2.0F;

        PoseStack pose = new PoseStack();
        pose.translate(WIDTH / 2.0F, HEIGHT / 2.0F, 0.0F);
        // Screen y points down: flip it so the structure's up is the image's up.
        pose.scale(scale, -scale, scale);
        pose.translate(-centerX, -centerY, 0.0F);
        pose.mulPose(rotation);
        pose.translate(-size.getX() / 2.0F, -size.getY() / 2.0F, -size.getZ() / 2.0F);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            List<StructureMesh.Quad> quads = mesh.quads().stream().filter(quad -> quad.layer() == layer).toList();
            if (quads.isEmpty()) {
                continue;
            }
            submits.submitCustomGeometry(pose, renderType(layer), (last, buffer) -> {
                PoseStack local = new PoseStack();
                local.last().set(last);
                for (StructureMesh.Quad quad : quads) {
                    local.pushPose();
                    local.translate(quad.x(), quad.y(), quad.z());
                    buffer.putBakedQuad(local.last(), quad.quad(), quad.instance());
                    local.popPose();
                }
            });
        }
    }

    private static RenderType renderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    /**
     * Projects the corners of the structure box with the camera rotation.
     *
     * @return minimum x, maximum x, minimum y, maximum y of the projected box, in blocks
     */
    static float[] projectedBounds(Vec3i size, Matrix4f rotation) {
        float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set(((i & 1) == 0 ? 0 : size.getX()) - size.getX() / 2.0F, ((i & 2) == 0 ? 0 : size.getY()) - size.getY() / 2.0F,
                    ((i & 4) == 0 ? 0 : size.getZ()) - size.getZ() / 2.0F);
            rotation.transformPosition(corner);
            bounds[0] = Math.min(bounds[0], corner.x);
            bounds[1] = Math.max(bounds[1], corner.x);
            bounds[2] = Math.min(bounds[2], corner.y);
            bounds[3] = Math.max(bounds[3], corner.y);
        }
        return bounds;
    }

    /** Copies the texture into a buffer and converts it to an image once the GPU is done; then frees the texture. */
    private static void readBack(GpuDevice device, GpuTexture texture, Consumer<NativeImage> onDone) {
        int pixelSize = texture.getFormat().blockSize();
        GpuBuffer buffer = device.createBuffer(() -> "Architect's Trials preview read-back", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                (long) WIDTH * HEIGHT * pixelSize);
        device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        image.setPixelABGR(x, HEIGHT - y - 1, view.data().getInt((x + y * WIDTH) * pixelSize));
                    }
                }
                onDone.accept(image);
            } finally {
                buffer.close();
                texture.close();
            }
        }, 0);
    }
}

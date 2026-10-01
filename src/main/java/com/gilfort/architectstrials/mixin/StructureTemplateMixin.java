package com.gilfort.architectstrials.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gilfort.architectstrials.structure.PaintingPlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps paintings with an even width or height in place when a structure template is placed (see
 * {@link PaintingPlacement}). Applies to every template placement, which also fixes the vanilla behavior.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {

    /**
     * Re-anchors paintings after the template moved them to their stored center.
     *
     * @param entity   the placed entity
     * @param x        the stored center x
     * @param y        the stored center y
     * @param z        the stored center z
     * @param yRot     the rotation
     * @param xRot     the pitch
     * @param original the original move
     */
    @WrapOperation(method = "lambda$addEntitiesToWorld$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;snapTo(DDDFF)V"))
    private static void architectstrials$anchorPaintings(Entity entity, double x, double y, double z, float yRot, float xRot,
            Operation<Void> original) {
        original.call(entity, x, y, z, yRot, xRot);
        if (entity instanceof Painting painting) {
            PaintingPlacement.anchorAt(painting, new Vec3(x, y, z));
        }
    }
}

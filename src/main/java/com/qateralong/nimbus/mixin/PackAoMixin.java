package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.pack.BlockIds;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Packs with separateAo get terrain colors without the occlusion and the occlusion itself in the vertex alpha.
@Mixin(ModelBlockRenderer.class)
public abstract class PackAoMixin {
    private static final float[] NIMBUS_ONES = {1.0F, 1.0F, 1.0F, 1.0F};

    @WrapOperation(method = "putQuadData", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void nimbus$packAo(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha,
                               int[] lightmap, int overlay, boolean readExisting, Operation<Void> original) {
        if (!BlockIds.on || !BlockIds.separateAo || !BlockIds.meshing() || consumer instanceof com.qateralong.nimbus.render.PackedBuilder) {
            original.call(consumer, pose, quad, brightness, red, green, blue, alpha, lightmap, overlay, readExisting);
            return;
        }
        BlockIds.ao(brightness);
        try {
            original.call(consumer, pose, quad, NIMBUS_ONES, red, green, blue, alpha, lightmap, overlay, readExisting);
        } finally {
            BlockIds.aoDone();
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.AnimatedSprites;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockSpriteMarkMixin {
    @Inject(method = "renderQuadList", at = @At("HEAD"))
    private static void nimbus$markList(PoseStack.Pose pose, VertexConsumer consumer, float red, float green, float blue, List<BakedQuad> quads,
                                        int light, int overlay, CallbackInfo ci) {
        AnimatedSprites.mark(quads);
    }

    @Inject(method = "putQuadData", at = @At("HEAD"))
    private void nimbus$markQuad(BlockAndTintGetter level, BlockState state, BlockPos pos, VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                                 float b0, float b1, float b2, float b3, int l0, int l1, int l2, int l3, int overlay, CallbackInfo ci) {
        if (AnimatedSprites.enabled && RenderSystem.isOnRenderThread()) {
            AnimatedSprites.mark(quad.getSprite());
        }
    }
}

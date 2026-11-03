package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.qateralong.nimbus.render.GuiBatch;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Deferred GUI work is drawn before a render type sets up its state, never in the middle of it
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BatchDrawMixin {
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("HEAD"))
    private void nimbus$drawStart(RenderType type, BufferBuilder builder, CallbackInfo ci) {
        GuiBatch.drawStart();
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("RETURN"))
    private void nimbus$drawEnd(RenderType type, BufferBuilder builder, CallbackInfo ci) {
        GuiBatch.drawEnd();
    }
}

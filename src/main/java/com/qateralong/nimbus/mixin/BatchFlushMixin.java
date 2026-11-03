package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.qateralong.nimbus.render.GuiBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public abstract class BatchFlushMixin {
    @Inject(method = {"drawWithShader", "draw"}, at = @At("HEAD"))
    private static void nimbus$flush(MeshData mesh, CallbackInfo ci) {
        GuiBatch.stateChanged();
    }

    @Inject(method = "drawWithShader", at = @At("HEAD"))
    private static void nimbus$icons(MeshData mesh, CallbackInfo ci) {
        com.qateralong.nimbus.render.ItemIcons.drawPending();
    }
}

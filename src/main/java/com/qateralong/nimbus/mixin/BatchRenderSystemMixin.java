package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.render.GuiBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class BatchRenderSystemMixin {
    @Inject(method = {"setShaderColor", "setProjectionMatrix", "applyModelViewMatrix"}, at = @At("HEAD"))
    private static void nimbus$state(CallbackInfo ci) {
        GuiBatch.stateChanged();
    }
}

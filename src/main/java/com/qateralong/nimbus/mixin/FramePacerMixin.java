package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.FramePacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSystem.class, remap = false)
public abstract class FramePacerMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void nimbus$pace(int fps, CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.preciseLimiter) {
            FramePacer.limit(fps);
            ci.cancel();
        }
    }
}

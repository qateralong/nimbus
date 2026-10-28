package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererGraphicsMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void nimbus$sky(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.sky) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void nimbus$weather(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.weather) {
            ci.cancel();
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleBreakMixin {
    @Inject(method = {"destroy", "crack"}, at = @At("HEAD"), cancellable = true)
    private void nimbus$breaking(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.blockBreakParticles) {
            ci.cancel();
        }
    }
}

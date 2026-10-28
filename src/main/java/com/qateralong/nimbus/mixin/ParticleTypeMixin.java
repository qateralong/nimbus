package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleTypeMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nimbus$type(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.disabledParticles.isEmpty()) {
            var key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
            if (key != null && config.disabledParticles.contains(key.toString())) {
                cir.setReturnValue(null);
            }
        }
    }
}

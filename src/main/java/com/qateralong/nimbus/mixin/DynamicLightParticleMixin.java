package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.DynamicLights;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Smoke, dust and other lit particles near a dynamic light.
@Mixin(Particle.class)
public abstract class DynamicLightParticleMixin {
    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;

    @Inject(method = "getLightColor", at = @At("RETURN"), cancellable = true)
    private void nimbus$dynamic(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (DynamicLights.active()) {
            cir.setReturnValue(DynamicLights.apply(cir.getReturnValueI(), this.x, this.y, this.z));
        }
    }
}

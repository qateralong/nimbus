package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.DynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Entities (and the first-person hand, which asks the same) lit by nearby dynamic lights.
@Mixin(EntityRenderer.class)
public abstract class DynamicLightEntityMixin<T extends Entity> {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void nimbus$dynamic(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (DynamicLights.active()) {
            Vec3 probe = entity.getLightProbePosition(partialTick);
            cir.setReturnValue(DynamicLights.apply(cir.getReturnValueI(), probe.x, probe.y, probe.z));
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.LightHolder;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityLightMixin {
    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void nimbus$cached(E entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.entityLightCache && ((LightHolder) entity).nimbus$lightTick() == Nimbus.ticks) {
            cir.setReturnValue(((LightHolder) entity).nimbus$light());
        }
    }

    @Inject(method = "getPackedLightCoords", at = @At("RETURN"))
    private <E extends Entity> void nimbus$store(E entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        ((LightHolder) entity).nimbus$setLight(Nimbus.ticks, cir.getReturnValueI());
    }
}

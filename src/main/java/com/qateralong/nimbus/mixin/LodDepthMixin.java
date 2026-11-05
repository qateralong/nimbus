package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.lod.LodRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class LodDepthMixin {
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void nimbus$far(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(LodRenderer.depthFar(cir.getReturnValueF()));
    }
}

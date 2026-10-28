package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionSpecialEffects.class)
public abstract class CloudHeightMixin {
    @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true)
    private void nimbus$height(CallbackInfoReturnable<Float> cir) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.cloudHeight > 0 && !Float.isNaN(cir.getReturnValue())) {
            cir.setReturnValue((float) config.cloudHeight);
        }
    }
}

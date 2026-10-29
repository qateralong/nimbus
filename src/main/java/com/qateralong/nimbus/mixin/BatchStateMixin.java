package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.render.GuiBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class BatchStateMixin {
    @Inject(method = "_enableBlend", at = @At("HEAD"))
    private static void nimbus$blendOn(CallbackInfo ci) {
        GuiBatch.blendChanged(true);
    }

    @Inject(method = "_disableBlend", at = @At("HEAD"))
    private static void nimbus$blendOff(CallbackInfo ci) {
        GuiBatch.blendChanged(false);
    }

    @Inject(method = {"_blendFunc", "_blendFuncSeparate", "_blendEquation", "_enableDepthTest", "_disableDepthTest", "_depthFunc", "_depthMask",
            "_enableScissorTest", "_disableScissorTest", "_scissorBox", "_colorMask", "_enableCull", "_disableCull"}, at = @At("HEAD"))
    private static void nimbus$state(CallbackInfo ci) {
        GuiBatch.stateChanged();
    }
}

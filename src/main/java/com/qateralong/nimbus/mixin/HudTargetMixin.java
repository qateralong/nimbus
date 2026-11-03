package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.qateralong.nimbus.render.HudCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// HUD code that draws into its own target and then rebinds the main one (minimaps) lands back in the HUD cache.
@Mixin(RenderTarget.class)
public abstract class HudTargetMixin {
    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void nimbus$hud(boolean viewport, CallbackInfo ci) {
        if (HudCache.redirect((RenderTarget) (Object) this, viewport)) {
            ci.cancel();
        }
    }
}

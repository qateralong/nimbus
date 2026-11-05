package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.Window;
import com.qateralong.nimbus.FrameLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowDisplayMixin {
    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void nimbus$fence(CallbackInfo ci) {
        FrameLimiter.afterSwap();
        com.qateralong.nimbus.render.Stream.endFrame();
        com.qateralong.nimbus.render.Terrain.endFrame();
        com.qateralong.nimbus.Nimbus.frames++;
        com.qateralong.nimbus.render.SlowFrames.endFrame();
        com.qateralong.nimbus.render.GuiBatch.endFrame();
        com.qateralong.nimbus.render.GpuTimer.endFrame();
    }
}

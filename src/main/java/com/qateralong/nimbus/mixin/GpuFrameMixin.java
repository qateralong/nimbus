package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.GpuTimer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class GpuFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void nimbus$frameStart(CallbackInfo ci) {
        GpuTimer.frameStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$frameEnd(CallbackInfo ci) {
        GpuTimer.frameEnd();
    }
}

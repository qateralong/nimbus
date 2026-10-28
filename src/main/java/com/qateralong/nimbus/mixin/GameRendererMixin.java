package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Scaler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void nimbus$begin(DeltaTracker delta, CallbackInfo ci) {
        Scaler.begin();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$end(DeltaTracker delta, CallbackInfo ci) {
        Scaler.end();
        com.qateralong.nimbus.Sharpen.apply(delta.getGameTimeDeltaPartialTick(false));
    }
}

package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void nimbus$batchBegin(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        com.qateralong.nimbus.render.GuiBatch.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void nimbus$batchEnd(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        com.qateralong.nimbus.render.GuiBatch.end();
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void nimbus$vignette(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.vignette) {
            ci.cancel();
        }
    }
}

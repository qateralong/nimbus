package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.pack.Pipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// underwaterOverlay=false in shaders.properties
@Mixin(ScreenEffectRenderer.class)
public abstract class PackWaterOverlayMixin {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void nimbus$packWater(Minecraft mc, PoseStack pose, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && !p.underwaterOverlay) {
            ci.cancel();
        }
    }
}

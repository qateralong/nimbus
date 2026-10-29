package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.MipFill;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpriteContents.class, priority = 1500)
public abstract class SodiumMipFillMixin {
    @Inject(method = "sodium$fillInTransparentPixelColors", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void nimbus$fill(NativeImage image, CallbackInfo ci) {
        if (NimbusConfig.get().enabled) {
            MipFill.fill(image);
            ci.cancel();
        }
    }
}

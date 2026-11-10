package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.MipFill;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Without Sodium: transparent texels take the colour of the visible ones before mipmaps are built (no dark fringes, no
// black holes in fast leaves)
@Mixin(SpriteContents.class)
public abstract class MipFillMixin {
    @Shadow
    @Final
    private NativeImage originalImage;

    @Inject(method = "increaseMipLevel", at = @At("HEAD"))
    private void nimbus$fill(int levels, CallbackInfo ci) {
        if (NimbusConfig.get().enabled) {
            MipFill.fill(this.originalImage);
        }
    }
}

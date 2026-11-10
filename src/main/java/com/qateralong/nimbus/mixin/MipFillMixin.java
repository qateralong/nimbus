package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.MipFill;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Without Sodium: mipmaps are built from a copy whose transparent texels take the colour of the visible ones (no dark
// fringes at a distance); the full-size texture stays as in vanilla
@Mixin(SpriteContents.class)
public abstract class MipFillMixin {
    @Shadow
    @Final
    private NativeImage originalImage;

    @Unique
    private int[] nimbus$saved;

    @Inject(method = "increaseMipLevel", at = @At("HEAD"))
    private void nimbus$fill(int levels, CallbackInfo ci) {
        if (levels > 0 && NimbusConfig.get().enabled && NimbusConfig.get().mipFill) {
            this.nimbus$saved = MipFill.fill(this.originalImage);
        }
    }

    @Inject(method = "increaseMipLevel", at = @At("RETURN"))
    private void nimbus$restore(int levels, CallbackInfo ci) {
        if (this.nimbus$saved != null) {
            MipFill.restore(this.originalImage, this.nimbus$saved);
            this.nimbus$saved = null;
        }
    }
}

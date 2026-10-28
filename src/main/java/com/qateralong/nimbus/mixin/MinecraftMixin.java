package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.qateralong.nimbus.Scaler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void nimbus$scaled(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = Scaler.redirect();
        if (target != null) {
            cir.setReturnValue(target);
        }
    }
}

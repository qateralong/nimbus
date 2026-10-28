package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.Scaler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlStateManagerMixin {
    @ModifyVariable(method = "_viewport", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int nimbus$width(int width) {
        return Scaler.viewportWidth(width);
    }

    @ModifyVariable(method = "_viewport", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static int nimbus$height(int height) {
        return Scaler.viewportHeight(height);
    }
}

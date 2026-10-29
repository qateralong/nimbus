package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Visibility;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class VisibilityResetMixin {
    @Inject(method = "allChanged", at = @At("HEAD"))
    private void nimbus$allChanged(CallbackInfo ci) {
        Visibility.invalidate();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void nimbus$setLevel(ClientLevel level, CallbackInfo ci) {
        Visibility.invalidate();
    }
}

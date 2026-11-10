package com.qateralong.nimbus.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public abstract class LazyViewAreaMixin {
    @Inject(method = "createSections", at = @At("HEAD"))
    private void nimbus$lazyOn(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        com.qateralong.nimbus.render.LazyBuffers.creatingSections = true;
    }

    @Inject(method = "createSections", at = @At("RETURN"))
    private void nimbus$lazyOff(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        com.qateralong.nimbus.render.LazyBuffers.creatingSections = false;
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.render.GlMirror;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlMirrorMixin {
    @Inject(method = "_enableBlend", at = @At("HEAD"))
    private static void nimbus$blendOn(CallbackInfo ci) {
        GlMirror.blend = true;
    }

    @Inject(method = "_disableBlend", at = @At("HEAD"))
    private static void nimbus$blendOff(CallbackInfo ci) {
        GlMirror.blend = false;
    }

    @Inject(method = "_blendFunc", at = @At("HEAD"), cancellable = true)
    private static void nimbus$func(int src, int dst, CallbackInfo ci) {
        if (GlMirror.overAlpha && dst == GL11.GL_ONE_MINUS_SRC_ALPHA) {
            ci.cancel();
            GlStateManager._blendFuncSeparate(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return;
        }
        GlMirror.srcRgb = GlMirror.srcAlpha = src;
        GlMirror.dstRgb = GlMirror.dstAlpha = dst;
    }

    @Inject(method = "_blendFuncSeparate", at = @At("HEAD"), cancellable = true)
    private static void nimbus$funcSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (GlMirror.overAlpha && dstRgb == GL11.GL_ONE_MINUS_SRC_ALPHA && (srcAlpha != GL11.GL_ONE || dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA)) {
            ci.cancel();
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return;
        }
        GlMirror.srcRgb = srcRgb;
        GlMirror.dstRgb = dstRgb;
        GlMirror.srcAlpha = srcAlpha;
        GlMirror.dstAlpha = dstAlpha;
    }

    @Inject(method = "_enableDepthTest", at = @At("HEAD"))
    private static void nimbus$depthOn(CallbackInfo ci) {
        GlMirror.depth = true;
    }

    @Inject(method = "_disableDepthTest", at = @At("HEAD"))
    private static void nimbus$depthOff(CallbackInfo ci) {
        GlMirror.depth = false;
    }

    @Inject(method = "_depthFunc", at = @At("HEAD"))
    private static void nimbus$depthFunc(int func, CallbackInfo ci) {
        GlMirror.depthFunc = func;
    }

    @Inject(method = "_depthMask", at = @At("HEAD"))
    private static void nimbus$depthMask(boolean mask, CallbackInfo ci) {
        GlMirror.depthMask = mask;
    }
}

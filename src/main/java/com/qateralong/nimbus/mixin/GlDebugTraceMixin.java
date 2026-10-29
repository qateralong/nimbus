package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.GlDebug;
import com.qateralong.nimbus.Nimbus;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public abstract class GlDebugTraceMixin {
    @Inject(method = "enableDebugCallback", at = @At("RETURN"))
    private static void nimbus$sync(int verbosity, boolean sync, CallbackInfo ci) {
        if (System.getProperty("nimbus.gltrace") != null) {
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }
    }

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void nimbus$trace(int source, int type, int id, int severity, int length, long message, long user, CallbackInfo ci) {
        if (System.getProperty("nimbus.gltrace") != null && type == GL43.GL_DEBUG_TYPE_ERROR) {
            Nimbus.LOGGER.warn("Nimbus GL error trace", new Throwable());
        }
    }
}

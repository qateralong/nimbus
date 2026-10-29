package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.render.StreamTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin {
    @Inject(method = "getImmediateDrawVertexBuffer", at = @At("RETURN"))
    private void nimbus$immediate(CallbackInfoReturnable<VertexBuffer> cir) {
        ((StreamTarget) cir.getReturnValue()).nimbus$markImmediate();
    }
}

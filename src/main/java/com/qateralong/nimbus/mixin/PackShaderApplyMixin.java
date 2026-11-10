package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.pack.Pipeline;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// World draws switch to the pack's gbuffers program right after vanilla set up its own.
@Mixin(ShaderInstance.class)
public abstract class PackShaderApplyMixin {
    @Inject(method = "apply", at = @At("RETURN"))
    private void nimbus$packApply(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null) {
            p.applied((ShaderInstance) (Object) this);
        }
    }
}

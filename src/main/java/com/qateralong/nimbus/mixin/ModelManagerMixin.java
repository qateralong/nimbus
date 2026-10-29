package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.QuadDedup;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelManager.class)
public abstract class ModelManagerMixin {
    @Inject(method = "loadModels", at = @At("HEAD"))
    private void nimbus$begin(CallbackInfoReturnable<?> cir) {
        QuadDedup.begin();
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void nimbus$end(CallbackInfo ci) {
        QuadDedup.end();
    }
}

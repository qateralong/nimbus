package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Idle;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundVolumeMixin {
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void nimbus$background(CallbackInfoReturnable<Float> cir) {
        float volume = Idle.volume(cir.getReturnValue());
        if (volume != cir.getReturnValue()) {
            cir.setReturnValue(volume);
        }
    }
}

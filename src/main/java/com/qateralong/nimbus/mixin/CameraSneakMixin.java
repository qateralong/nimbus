package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraSneakMixin {
    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Inject(method = "tick", at = @At("TAIL"))
    private void nimbus$instant(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.instantSneak && this.entity != null) {
            this.eyeHeight = this.entity.getEyeHeight();
            this.eyeHeightOld = this.eyeHeight;
        }
    }
}

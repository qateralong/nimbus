package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Startup;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class StartupMixin {
    @Inject(method = "onResourceLoadFinished", at = @At("RETURN"))
    private void nimbus$loaded(CallbackInfo ci) {
        Startup.reloadEnd();
    }
}

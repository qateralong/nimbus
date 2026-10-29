package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Startup;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectTimingMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void nimbus$join(CallbackInfo ci) {
        Startup.joining();
    }
}

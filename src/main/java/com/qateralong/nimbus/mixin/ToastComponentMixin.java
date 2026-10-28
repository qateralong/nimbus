package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void nimbus$toast(Toast toast, CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.toasts) {
            ci.cancel();
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.Cull;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void nimbus$line(CallbackInfoReturnable<List<String>> cir) {
        if (NimbusConfig.get().debugLine) {
            cir.getReturnValue().add(NimbusConfig.get().enabled ? Cull.last : "Nimbus: выключен");
        }
    }
}

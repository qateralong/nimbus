package com.qateralong.boost.mixin;

import com.qateralong.boost.BoostConfig;
import com.qateralong.boost.Cull;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void boost$line(CallbackInfoReturnable<List<String>> cir) {
        if (BoostConfig.get().debugLine) {
            cir.getReturnValue().add(BoostConfig.get().enabled ? Cull.last : "Boost: выключен");
        }
    }
}

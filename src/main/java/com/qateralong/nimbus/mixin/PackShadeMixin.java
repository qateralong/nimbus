package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.pack.Pipeline;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Packs light faces themselves; vanilla's per-direction darkening stays off unless the pack asks for oldLighting.
@Mixin(ClientLevel.class)
public abstract class PackShadeMixin {
    @Inject(method = "getShade", at = @At("HEAD"), cancellable = true)
    private void nimbus$packShade(Direction direction, boolean shade, CallbackInfoReturnable<Float> cir) {
        Pipeline p = Pipeline.get();
        if (p != null && !p.oldLighting) {
            cir.setReturnValue(1.0F);
        }
    }
}

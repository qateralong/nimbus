package com.qateralong.nimbus.mixin;

import net.minecraft.util.MemoryReserve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla keeps 10 MB of heap aside for its out-of-memory screen.
@Mixin(MemoryReserve.class)
public abstract class MemoryReserveMixin {
    @Inject(method = "allocate", at = @At("HEAD"), cancellable = true)
    private static void nimbus$skip(CallbackInfo ci) {
        ci.cancel();
    }
}

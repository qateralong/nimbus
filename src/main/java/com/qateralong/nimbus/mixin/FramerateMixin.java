package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Idle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class FramerateMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void nimbus$idle(CallbackInfoReturnable<Integer> cir) {
        int limited = Idle.limit(cir.getReturnValue());
        if (limited != cir.getReturnValue()) {
            cir.setReturnValue(limited);
        }
    }
}

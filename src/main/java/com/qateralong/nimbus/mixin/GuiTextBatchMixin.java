package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.GuiBatch;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiTextBatchMixin {
    @Shadow
    private boolean managed;

    @Inject(method = "flushIfUnmanaged", at = @At("HEAD"), cancellable = true)
    private void nimbus$defer(CallbackInfo ci) {
        if (!this.managed && GuiBatch.deferText((GuiGraphics) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "flush", at = @At("HEAD"))
    private void nimbus$flushed(CallbackInfo ci) {
        GuiBatch.textFlushed((GuiGraphics) (Object) this);
    }
}

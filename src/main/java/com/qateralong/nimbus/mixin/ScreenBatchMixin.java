package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.GuiBatch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBatchMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void nimbus$begin(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiBatch.begin();
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void nimbus$end(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiBatch.end();
    }
}

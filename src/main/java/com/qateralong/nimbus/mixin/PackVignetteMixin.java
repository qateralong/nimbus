package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.pack.Pipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// vignette=false in shaders.properties
@Mixin(Gui.class)
public abstract class PackVignetteMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void nimbus$packVignette(GuiGraphics graphics, Entity entity, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && !p.vignette) {
            ci.cancel();
        }
    }
}

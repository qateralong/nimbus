package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.lod.LodRenderer;
import com.qateralong.nimbus.lod.LodStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LodGameMixin {
    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void nimbus$leave(Screen next, CallbackInfo ci) {
        LodRenderer.leave();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$close(CallbackInfo ci) {
        LodRenderer.leave();
        LodStore.close();
    }
}

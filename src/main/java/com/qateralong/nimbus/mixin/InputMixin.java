package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Idle;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class InputMixin {
    @Inject(method = {"onMove", "onPress", "onScroll"}, at = @At("HEAD"))
    private void nimbus$input(CallbackInfo ci) {
        Idle.input();
    }

    @Inject(method = {"onPress", "onScroll"}, at = @At("HEAD"))
    private void nimbus$hud(CallbackInfo ci) {
        com.qateralong.nimbus.render.HudCache.invalidate();
    }
}

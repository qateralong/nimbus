package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Idle;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = {"keyPress", "charTyped"}, at = @At("HEAD"))
    private void nimbus$input(CallbackInfo ci) {
        Idle.input();
        com.qateralong.nimbus.render.HudCache.invalidate();
    }

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void nimbus$bug(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window == net.minecraft.client.Minecraft.getInstance().getWindow().getWindow()) {
            com.qateralong.nimbus.BugReport.keyPressed(key, scanCode, action);
        }
    }
}

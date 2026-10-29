package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Startup;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public abstract class JoinTimingMixin {
    @Inject(method = "openWorld", at = @At("HEAD"))
    private void nimbus$join(String level, Runnable cancel, CallbackInfo ci) {
        Startup.joining();
    }
}

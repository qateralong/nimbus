package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.FastQuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FastQuitMixin {
    // the "Saving world" loop: leave it at once, the server keeps saving in the background
    @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
    private boolean nimbus$fastQuit(IntegratedServer server, Operation<Boolean> original) {
        return original.call(server) || FastQuit.leave(server);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void nimbus$awaitSaves(CallbackInfo ci) {
        FastQuit.closing = true;
        FastQuit.await(null);
    }
}

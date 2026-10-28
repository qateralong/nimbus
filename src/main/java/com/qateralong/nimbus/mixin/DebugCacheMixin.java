package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DebugScreenOverlay.class, priority = 900)
public abstract class DebugCacheMixin {
    @Unique
    private List<String> nimbus$game;
    @Unique
    private List<String> nimbus$system;
    @Unique
    private long nimbus$gameTime;
    @Unique
    private long nimbus$systemTime;

    @Unique
    private static boolean nimbus$steady() {
        NimbusConfig config = NimbusConfig.get();
        return config.enabled && config.steadyDebug;
    }

    @Inject(method = "getGameInformation", at = @At("HEAD"), cancellable = true)
    private void nimbus$cachedGame(CallbackInfoReturnable<List<String>> cir) {
        if (nimbus$steady() && this.nimbus$game != null && System.currentTimeMillis() - this.nimbus$gameTime < 100) {
            cir.setReturnValue(new ArrayList<>(this.nimbus$game));
        }
    }

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void nimbus$storeGame(CallbackInfoReturnable<List<String>> cir) {
        if (System.currentTimeMillis() - this.nimbus$gameTime >= 100) {
            this.nimbus$game = new ArrayList<>(cir.getReturnValue());
            this.nimbus$gameTime = System.currentTimeMillis();
        }
    }

    @Inject(method = "getSystemInformation", at = @At("HEAD"), cancellable = true)
    private void nimbus$cachedSystem(CallbackInfoReturnable<List<String>> cir) {
        if (nimbus$steady() && this.nimbus$system != null && System.currentTimeMillis() - this.nimbus$systemTime < 100) {
            cir.setReturnValue(new ArrayList<>(this.nimbus$system));
        }
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void nimbus$storeSystem(CallbackInfoReturnable<List<String>> cir) {
        if (System.currentTimeMillis() - this.nimbus$systemTime >= 100) {
            this.nimbus$system = new ArrayList<>(cir.getReturnValue());
            this.nimbus$systemTime = System.currentTimeMillis();
        }
    }
}

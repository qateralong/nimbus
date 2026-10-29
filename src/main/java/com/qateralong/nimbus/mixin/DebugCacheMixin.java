package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.NimbusConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DebugScreenOverlay.class)
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

    @WrapOperation(method = "drawGameInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;getGameInformation()Ljava/util/List;"))
    private List<String> nimbus$game(DebugScreenOverlay overlay, Operation<List<String>> original) {
        long now = System.currentTimeMillis();
        if (nimbus$steady() && this.nimbus$game != null && now - this.nimbus$gameTime < 100) {
            return new ArrayList<>(this.nimbus$game);
        }
        List<String> lines = original.call(overlay);
        this.nimbus$game = new ArrayList<>(lines);
        this.nimbus$gameTime = now;
        return lines;
    }

    @WrapOperation(method = "drawSystemInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;getSystemInformation()Ljava/util/List;"))
    private List<String> nimbus$system(DebugScreenOverlay overlay, Operation<List<String>> original) {
        long now = System.currentTimeMillis();
        if (nimbus$steady() && this.nimbus$system != null && now - this.nimbus$systemTime < 100) {
            return new ArrayList<>(this.nimbus$system);
        }
        List<String> lines = original.call(overlay);
        this.nimbus$system = new ArrayList<>(lines);
        this.nimbus$systemTime = now;
        return lines;
    }
}

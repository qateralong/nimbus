package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.AsyncScreenshot;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @Shadow
    private static File getFile(File directory) {
        throw new AssertionError();
    }

    @Inject(method = "_grab", at = @At("HEAD"), cancellable = true)
    private static void nimbus$async(File gameDirectory, @Nullable String name, RenderTarget target, Consumer<Component> messages, CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || !config.asyncScreenshots) {
            return;
        }
        File folder = new File(gameDirectory, "screenshots");
        folder.mkdir();
        File file = name == null ? getFile(folder) : new File(folder, name);
        if (AsyncScreenshot.start(target, file, messages)) {
            ci.cancel();
        }
    }
}

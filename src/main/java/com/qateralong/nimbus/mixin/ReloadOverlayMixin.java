package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Resource reloads after startup (packs, language, F3+T) keep the game or menu on screen with a thin progress line on top
// instead of the full-screen logo; input stays blocked until the reload is done, as in vanilla.
@Mixin(LoadingOverlay.class)
public abstract class ReloadOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private float currentProgress;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nimbus$quietReload(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (!this.fadeIn || !NimbusConfig.get().enabled || !NimbusConfig.get().quietReload) {
            return;
        }
        ci.cancel();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (this.minecraft.screen != null) {
            this.minecraft.screen.render(graphics, 0, 0, partial);
        }
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.05F, 0.0F, 1.0F);
        graphics.fill(0, 0, width, 2, 0x60000000);
        graphics.fill(0, 0, Math.round(width * this.currentProgress), 2, 0xFFFFFFFF);
        if (this.reload.isDone()) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }
            this.minecraft.setOverlay(null);
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, width, height);
            }
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.render.GuiBatch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsBatchMixin {
    @Shadow
    @Final
    private PoseStack pose;

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"), cancellable = true)
    private void nimbus$blit(ResourceLocation location, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, CallbackInfo ci) {
        if (GuiBatch.blit(location, this.pose.last().pose(), x1, x2, y1, y2, z, u1, u2, v1, v2)) {
            ci.cancel();
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void nimbus$blitColored(ResourceLocation location, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, float red,
                                    float green, float blue, float alpha, CallbackInfo ci) {
        if (GuiBatch.blit(location, this.pose.last().pose(), x1, x2, y1, y2, z, u1, u2, v1, v2, red, green, blue, alpha)) {
            ci.cancel();
        }
    }
}

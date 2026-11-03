package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.SignTextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SignRenderer.class)
public abstract class SignTextMixin {
    @WrapOperation(method = "renderSignText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int nimbus$text(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f pose, MultiBufferSource buffer,
                            Font.DisplayMode mode, int background, int light, Operation<Integer> original) {
        if (!shadow && NimbusConfig.get().enabled && NimbusConfig.get().signTextCache
                && SignTextCache.replay(text, 0, x, y, color, SignTextCache.extra(mode, background), pose, buffer, light,
                (p, b, l) -> original.call(font, text, x, y, color, false, p, b, mode, background, l))) {
            return 0;
        }
        return original.call(font, text, x, y, color, shadow, pose, buffer, mode, background, light);
    }

    @WrapOperation(method = "renderSignText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void nimbus$outline(Font font, FormattedCharSequence text, float x, float y, int color, int outline, Matrix4f pose, MultiBufferSource buffer,
                                int light, Operation<Void> original) {
        if (NimbusConfig.get().enabled && NimbusConfig.get().signTextCache
                && SignTextCache.replay(text, 1, x, y, color, outline, pose, buffer, light,
                (p, b, l) -> original.call(font, text, x, y, color, outline, p, b, l))) {
            return;
        }
        original.call(font, text, x, y, color, outline, pose, buffer, light);
    }
}

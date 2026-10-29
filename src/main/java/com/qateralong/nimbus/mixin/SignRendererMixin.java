package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.Cull;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {
    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void nimbus$text(BlockPos pos, SignText text, PoseStack pose, MultiBufferSource buffers, int light, int lineHeight, int maxWidth, boolean front,
                            CallbackInfo ci) {
        if (!Cull.near(pos, NimbusConfig.get().signTextDistance)) {
            ci.cancel();
            return;
        }
        NimbusConfig config = NimbusConfig.get();
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (config.enabled && config.signBackCulling && minecraft.level != null && minecraft.player != null && !minecraft.options.getCameraType().isMirrored()
                && minecraft.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                && sign.isFacingFrontText(minecraft.player) != front) {
            ci.cancel();
        }
    }
}

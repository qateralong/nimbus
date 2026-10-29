package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.Chests;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HangingSignRenderer.class)
public abstract class HangingSignBoardMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"))
    private void nimbus$begin(SignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        Chests.currentSign = sign.getBlockState().getBlock();
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/SignBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("RETURN"))
    private void nimbus$end(SignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        Chests.currentSign = null;
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.Chests;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
public abstract class SignBoardMixin {
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

    @Inject(method = "renderSign", at = @At("HEAD"), cancellable = true)
    private void nimbus$board(PoseStack pose, MultiBufferSource buffers, int light, int overlay, WoodType wood, Model model, CallbackInfo ci) {
        if (Chests.currentSign != null && Chests.meshedSign(Chests.currentSign)) {
            ci.cancel();
        }
    }
}

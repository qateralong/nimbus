package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionCompiler.class)
public abstract class SectionPoseMixin {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
    private RandomSource nimbus$random(Operation<RandomSource> original) {
        return NimbusConfig.get().enabled ? new SingleThreadedRandomSource(RandomSupport.generateUniqueSeed()) : original.call();
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private void nimbus$push(PoseStack stack, Operation<Void> original) {
        if (!NimbusConfig.get().enabled) {
            original.call(stack);
        }
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void nimbus$translate(PoseStack stack, float x, float y, float z, Operation<Void> original) {
        if (NimbusConfig.get().enabled) {
            stack.last().pose().translation(x, y, z);
        } else {
            original.call(stack, x, y, z);
        }
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void nimbus$pop(PoseStack stack, Operation<Void> original) {
        if (!NimbusConfig.get().enabled && !stack.clear()) {
            original.call(stack);
        }
    }
}

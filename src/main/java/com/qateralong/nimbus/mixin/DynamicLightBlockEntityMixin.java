package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.qateralong.nimbus.render.DynamicLights;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Chests, signs and other block entities lit by nearby dynamic lights.
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class DynamicLightBlockEntityMixin {
    @ModifyExpressionValue(method = "setupAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private static int nimbus$dynamic(int packed, @Local(argsOnly = true) BlockEntity blockEntity) {
        if (!DynamicLights.active()) {
            return packed;
        }
        BlockPos pos = blockEntity.getBlockPos();
        return DynamicLights.apply(packed, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}

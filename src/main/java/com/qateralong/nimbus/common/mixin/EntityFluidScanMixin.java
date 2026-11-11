package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.BlockCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The water and lava scan over the blocks an entity touches reads them through the last chunk.
@Mixin(Entity.class)
public abstract class EntityFluidScanMixin {
    @Inject(method = "updateFluidHeightAndDoFluidPushing", at = @At("HEAD"))
    private void nimbus$start(TagKey<Fluid> fluidTag, double motionScale, CallbackInfoReturnable<Boolean> cir) {
        BlockCursor.reset();
    }

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState nimbus$fluid(Level level, BlockPos pos, Operation<FluidState> original) {
        BlockCursor cursor = BlockCursor.of(level);
        return cursor == null ? original.call(level, pos) : cursor.fluidIfReady(level, pos);
    }
}

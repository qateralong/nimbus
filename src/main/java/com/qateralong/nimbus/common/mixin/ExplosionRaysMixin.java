package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.BlockCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The 1352 rays of an explosion step through ~30 000 block positions, two chunk lookups each; read them through the
// last chunk instead.
@Mixin(Explosion.class)
public abstract class ExplosionRaysMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void nimbus$start(CallbackInfo ci) {
        BlockCursor.reset();
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void nimbus$end(CallbackInfo ci) {
        BlockCursor.reset();
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nimbus$state(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockCursor cursor = BlockCursor.of(level);
        return cursor == null ? original.call(level, pos) : cursor.state(level, pos);
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState nimbus$fluid(Level level, BlockPos pos, Operation<FluidState> original) {
        BlockCursor cursor = BlockCursor.of(level);
        return cursor == null ? original.call(level, pos) : cursor.fluid(level, pos);
    }
}

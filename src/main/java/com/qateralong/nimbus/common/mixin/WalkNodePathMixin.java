package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.qateralong.nimbus.common.PathCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodePathMixin {
    @Shadow
    protected static PathType getPathTypeFromState(BlockGetter level, BlockPos pos) {
        throw new AssertionError();
    }

    @WrapMethod(method = "getPathTypeFromState")
    private static PathType nimbus$byState(BlockGetter level, BlockPos pos, Operation<PathType> original) {
        BlockState state = level.getBlockState(pos);
        PathType type = PathCache.cached(state);
        if (type == null) {
            type = original.call(level, pos);
            PathCache.store(state, type);
            if (PathCache.compute == null) {
                PathCache.compute = WalkNodePathMixin::nimbus$compute;
            }
        }
        return type;
    }

    // the same computation for a state alone, read through a one-block getter
    private static PathType nimbus$compute(BlockState state) {
        return getPathTypeFromState(new net.minecraft.world.level.BlockGetter() {
            @Override
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return state;
            }

            @Override
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        }, BlockPos.ZERO);
    }

    @Inject(method = "checkNeighbourBlocks", at = @At("HEAD"), cancellable = true)
    private static void nimbus$quiet(PathfindingContext context, int x, int y, int z, PathType type, CallbackInfoReturnable<PathType> cir) {
        if (PathCache.quietAround(context.level(), x, y, z)) {
            cir.setReturnValue(type);
        }
    }
}

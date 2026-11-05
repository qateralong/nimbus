package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// "Can the fluid pass between these two blocks" went through a thread-local LRU cache with a new key per call. The
// common answers need no cache: a full block on either side always blocks, two empty shapes never do (the same
// results Shapes.mergedFaceOccludes gives); everything else still takes vanilla's path.
@Mixin(FlowingFluid.class)
public abstract class FluidWallMixin {
    @Inject(method = "canPassThroughWall", at = @At("HEAD"), cancellable = true)
    private void nimbus$plain(Direction direction, BlockGetter level, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState, CallbackInfoReturnable<Boolean> cir) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape == Shapes.block()) {
            cir.setReturnValue(false);
            return;
        }
        VoxelShape spread = spreadState.getCollisionShape(level, spreadPos);
        if (spread == Shapes.block()) {
            cir.setReturnValue(false);
        } else if (shape.isEmpty() && spread.isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}

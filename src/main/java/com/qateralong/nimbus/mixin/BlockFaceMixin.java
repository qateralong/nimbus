package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Leaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockFaceMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void nimbus$leaves(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbour, CallbackInfoReturnable<Boolean> cir) {
        if (Leaves.hide(state, level, neighbour, face)) {
            cir.setReturnValue(false);
        }
    }
}

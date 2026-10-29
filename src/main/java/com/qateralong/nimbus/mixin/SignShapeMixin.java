package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseEntityBlock.class)
public abstract class SignShapeMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void nimbus$sign(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (Chests.meshedSign(state.getBlock())) {
            cir.setReturnValue(RenderShape.MODEL);
        }
    }
}

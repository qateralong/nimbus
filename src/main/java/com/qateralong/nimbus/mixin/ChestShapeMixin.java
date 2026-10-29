package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChestBlock.class, EnderChestBlock.class})
public abstract class ChestShapeMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void nimbus$model(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (Chests.enabled()) {
            cir.setReturnValue(RenderShape.MODEL);
        }
    }
}

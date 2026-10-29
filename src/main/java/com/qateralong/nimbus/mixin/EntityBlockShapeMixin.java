package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BedBlock.class, ShulkerBoxBlock.class})
public abstract class EntityBlockShapeMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void nimbus$model(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if ((Object) this instanceof BedBlock ? Chests.bedsEnabled() : Chests.shulkersEnabled()) {
            cir.setReturnValue(RenderShape.MODEL);
        }
    }
}

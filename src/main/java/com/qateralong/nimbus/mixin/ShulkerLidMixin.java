package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerLidMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private static void nimbus$lid(Level level, BlockPos pos, BlockState state, ShulkerBoxBlockEntity shulker, CallbackInfo ci) {
        Chests.update(shulker, shulker.getProgress(1.0F));
    }
}

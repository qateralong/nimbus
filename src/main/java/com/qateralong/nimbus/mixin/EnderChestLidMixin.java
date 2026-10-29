package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestLidMixin {
    @Inject(method = "lidAnimateTick", at = @At("RETURN"))
    private static void nimbus$lid(Level level, BlockPos pos, BlockState state, EnderChestBlockEntity chest, CallbackInfo ci) {
        Chests.update(chest, chest.getOpenNess(1.0F));
    }
}

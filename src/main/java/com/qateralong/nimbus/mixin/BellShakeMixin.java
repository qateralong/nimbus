package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BellBlockEntity.class)
public abstract class BellShakeMixin {
    @Inject(method = "clientTick", at = @At("RETURN"))
    private static void nimbus$shake(Level level, BlockPos pos, BlockState state, BellBlockEntity bell, CallbackInfo ci) {
        Chests.update(bell, bell.shaking ? 1.0F : 0.0F);
    }
}

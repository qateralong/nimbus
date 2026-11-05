package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The per-thread 1024-entry cache in front of the temperature costs more than the noise lookup it saves.
@Mixin(Biome.class)
public abstract class BiomeTemperatureMixin {
    @Shadow
    private float getHeightAdjustedTemperature(BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "getTemperature", at = @At("HEAD"), cancellable = true)
    private void nimbus$direct(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(this.getHeightAdjustedTemperature(pos));
    }
}

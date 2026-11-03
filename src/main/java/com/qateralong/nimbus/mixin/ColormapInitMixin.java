package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.ColormapStamp;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GrassColor.class, FoliageColor.class})
public abstract class ColormapInitMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private static void nimbus$reloaded(int[] pixels, CallbackInfo ci) {
        ColormapStamp.value++;
    }
}

package com.qateralong.nimbus.common.mixin;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every palette resize made a new (factory, bits) record; there are only a few distinct ones.
@Mixin(targets = {"net.minecraft.world.level.chunk.PalettedContainer$Strategy$1", "net.minecraft.world.level.chunk.PalettedContainer$Strategy$2"})
public abstract class PaletteConfigMixin {
    @Unique
    private static final ConcurrentHashMap<Object, Object> NIMBUS$CONFIGS = new ConcurrentHashMap<>();

    @Inject(method = "getConfiguration", at = @At("RETURN"), cancellable = true)
    private void nimbus$intern(CallbackInfoReturnable<Object> cir) {
        Object config = cir.getReturnValue();
        Object shared = NIMBUS$CONFIGS.putIfAbsent(config, config);
        if (shared != null) {
            cir.setReturnValue(shared);
        }
    }
}

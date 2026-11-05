package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.IdleTicker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Idle hoppers and furnaces do their bookkeeping without the wrapper's block state read, validity check and profiler.
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityIdleTickMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void nimbus$idle(CallbackInfo ci) {
        if (this.blockEntity instanceof IdleTicker idle && idle.nimbus$idleTick()) {
            ci.cancel();
        }
    }
}

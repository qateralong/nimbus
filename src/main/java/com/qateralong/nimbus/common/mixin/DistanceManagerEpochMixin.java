package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.TicketEpoch;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DistanceManager.class)
public abstract class DistanceManagerEpochMixin {
    @Inject(method = "runAllUpdates", at = @At("HEAD"))
    private void nimbus$epoch(ChunkMap chunkMap, CallbackInfoReturnable<Boolean> cir) {
        TicketEpoch.value++;
    }
}

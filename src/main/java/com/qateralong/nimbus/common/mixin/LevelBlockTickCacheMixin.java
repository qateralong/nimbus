package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.TicketEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Block entity tickers come grouped by chunk; ask once per run of the same chunk whether it ticks.
@Mixin(Level.class)
public abstract class LevelBlockTickCacheMixin {
    @Unique
    private long nimbus$tickChunk;
    @Unique
    private int nimbus$tickEpoch;
    @Unique
    private boolean nimbus$tickCached;
    @Unique
    private boolean nimbus$tickResult;

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void nimbus$reset(CallbackInfo ci) {
        this.nimbus$tickCached = false;
    }

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean nimbus$ticks(Level level, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        int epoch = TicketEpoch.value;
        if (this.nimbus$tickCached && chunk == this.nimbus$tickChunk && epoch == this.nimbus$tickEpoch) {
            return this.nimbus$tickResult;
        }
        boolean result = level.shouldTickBlocksAt(pos);
        this.nimbus$tickChunk = chunk;
        this.nimbus$tickEpoch = epoch;
        this.nimbus$tickResult = result;
        this.nimbus$tickCached = true;
        return result;
    }
}

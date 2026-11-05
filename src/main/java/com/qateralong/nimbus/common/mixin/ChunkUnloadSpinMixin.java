package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A chunk waiting to unload while generation still holds it re-queued itself straight into the unload queue, which the
// tick keeps draining while it has time left: a busy loop that burned the spare tick time. Such chunks now retry on
// the next tick. Saves outside the tick (save-all flush, shutdown) take them back through vanilla's path.
@Mixin(ChunkMap.class)
public abstract class ChunkUnloadSpinMixin {
    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Shadow
    protected abstract void scheduleUnload(long chunkPos, ChunkHolder chunkHolder);

    @Unique
    private boolean nimbus$ticking;
    @Unique
    private List<ChunkHolder> nimbus$later = new ArrayList<>();

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void nimbus$start(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        this.nimbus$ticking = true;
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void nimbus$end(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        this.nimbus$ticking = false;
    }

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void nimbus$retry(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        if (this.nimbus$later.isEmpty()) {
            return;
        }
        List<ChunkHolder> later = this.nimbus$later;
        this.nimbus$later = new ArrayList<>();
        for (ChunkHolder holder : later) {
            long pos = holder.getPos().toLong();
            if (this.pendingUnloads.get(pos) == holder) {
                this.scheduleUnload(pos, holder);
            }
        }
    }

    @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
    private void nimbus$wait(long chunkPos, ChunkHolder holder, CallbackInfo ci) {
        if (this.nimbus$ticking && holder.getSaveSyncFuture().isDone() && !holder.isReadyForSaving()) {
            this.nimbus$later.add(holder);
            ci.cancel();
        }
    }
}

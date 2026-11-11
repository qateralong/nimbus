package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.TickQueue;
import com.qateralong.nimbus.common.TickSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Grouped tick queue and a packed-position set instead of a heap and an object hash set (see TickQueue, TickSet).
@Mixin(value = LevelChunkTicks.class, priority = 900)
public abstract class ChunkTickQueueMixin<T> {
    @Shadow
    @Final
    @Mutable
    private Queue<ScheduledTick<T>> tickQueue;
    @Shadow
    @Final
    @Mutable
    private Set<ScheduledTick<?>> ticksPerPosition;

    @Inject(method = "<init>()V", at = @At("TAIL"))
    private void nimbus$init(CallbackInfo ci) {
        this.tickQueue = new TickQueue<>();
        this.ticksPerPosition = new TickSet();
    }

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("TAIL"))
    private void nimbus$initSaved(List<SavedTick<T>> pending, CallbackInfo ci) {
        this.tickQueue = new TickQueue<>();
        TickSet set = new TickSet();
        for (SavedTick<T> tick : pending) {
            set.add(tick.pos().asLong(), tick.type());
        }
        this.ticksPerPosition = set;
    }

    /**
     * @author qateralong
     * @reason probe without allocating a ScheduledTick and an immutable BlockPos
     */
    @Overwrite
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return this.ticksPerPosition instanceof TickSet set ? set.contains(pos.asLong(), type)
                : this.ticksPerPosition.contains(ScheduledTick.probe(type, pos));
    }
}

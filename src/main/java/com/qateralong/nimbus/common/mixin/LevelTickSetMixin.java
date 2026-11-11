package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.TickSet;
import java.util.Set;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Repeaters and comparators ask willTickThisTick, which fills this set with every tick of the game tick and then
// removes them one by one as they run.
@Mixin(value = LevelTicks.class, priority = 900)
public abstract class LevelTickSetMixin<T> {
    @Shadow
    @Final
    @Mutable
    private Set<ScheduledTick<?>> toRunThisTickSet;

    @Shadow
    private void calculateTickSetIfNeeded() {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nimbus$init(CallbackInfo ci) {
        this.toRunThisTickSet = new TickSet();
    }

    /**
     * @author qateralong
     * @reason probe without allocating a ScheduledTick and an immutable BlockPos
     */
    @Overwrite
    public boolean willTickThisTick(BlockPos pos, T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet instanceof TickSet set ? set.contains(pos.asLong(), type)
                : this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }
}

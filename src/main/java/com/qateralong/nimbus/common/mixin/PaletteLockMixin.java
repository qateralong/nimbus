package com.qateralong.nimbus.common.mixin;

import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// A debugging trap for chunk data touched from two threads: a semaphore on every block write and container read.
@Mixin(PalettedContainer.class)
public abstract class PaletteLockMixin {
    @Unique
    private static final ThreadingDetector NIMBUS$UNUSED = new ThreadingDetector("PalettedContainer");

    // nothing locks it any more: one shared detector instead of a detector, semaphore and lock per container
    @Redirect(method = "<init>*", at = @At(value = "NEW", target = "net/minecraft/util/ThreadingDetector"), require = 0)
    private static ThreadingDetector nimbus$shared(String name) {
        return NIMBUS$UNUSED;
    }

    /**
     * @author qateralong
     * @reason the misuse check costs a semaphore per call and never changes what is stored
     */
    @Overwrite
    public void acquire() {
    }

    /**
     * @author qateralong
     * @reason see acquire
     */
    @Overwrite
    public void release() {
    }
}

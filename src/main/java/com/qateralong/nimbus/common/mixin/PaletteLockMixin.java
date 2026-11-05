package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// A debugging trap for chunk data touched from two threads: a semaphore on every block write and container read.
@Mixin(PalettedContainer.class)
public abstract class PaletteLockMixin {
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

package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

// hoppers ask a composter for its slots on every transfer attempt; the answer never changes
@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$OutputContainer")
public abstract class ComposterOutputMixin {
    @Unique
    private static final int[] NONE = new int[0];
    @Unique
    private static final int[] FIRST = {0};

    /**
     * @author qateralong
     * @reason shared arrays
     */
    @Overwrite
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? FIRST : NONE;
    }
}

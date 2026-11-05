package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Direction.class)
public abstract class DirectionOppositeMixin {
    @Shadow
    @Final
    private int oppositeIndex;

    @Shadow
    public static Direction from3DDataValue(int index) {
        throw new AssertionError();
    }

    @Unique
    private Direction nimbus$opposite;

    /**
     * @author qateralong
     * @reason one field read instead of a modulo, abs and table lookup on a very hot call
     */
    @Overwrite
    public Direction getOpposite() {
        Direction opposite = this.nimbus$opposite;
        if (opposite == null) {
            opposite = from3DDataValue(this.oppositeIndex);
            this.nimbus$opposite = opposite;
        }
        return opposite;
    }
}

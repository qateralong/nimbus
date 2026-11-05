package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.IdleTicker;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// A crafter that is not counting down does nothing in its tick.
@Mixin(CrafterBlockEntity.class)
public abstract class CrafterIdleMixin implements IdleTicker {
    @Shadow
    private int craftingTicksRemaining;

    @Override
    public boolean nimbus$idleTick() {
        return this.craftingTicksRemaining <= 0 && (Object) this.getClass() == CrafterBlockEntity.class;
    }
}

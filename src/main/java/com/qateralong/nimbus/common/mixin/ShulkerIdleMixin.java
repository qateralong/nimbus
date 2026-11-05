package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.IdleTicker;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// A closed shulker box at rest only rewrites zero progress.
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerIdleMixin implements IdleTicker {
    @Shadow
    private ShulkerBoxBlockEntity.AnimationStatus animationStatus;
    @Shadow
    private float progress;
    @Shadow
    private float progressOld;

    @Override
    public boolean nimbus$idleTick() {
        return (Object) this.getClass() == ShulkerBoxBlockEntity.class && this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.CLOSED && this.progress == 0.0F && this.progressOld == 0.0F;
    }
}

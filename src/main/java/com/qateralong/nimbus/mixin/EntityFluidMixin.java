package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityFluidMixin {
    @Shadow
    public int tickCount;

    @Unique
    private AABB nimbus$fluidBox;
    @Unique
    private int nimbus$fluidTick;
    @Unique
    private boolean nimbus$fluidResult;

    @Shadow
    public abstract AABB getBoundingBox();

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"), cancellable = true)
    private void nimbus$cached(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide() || self.isControlledByLocalInstance() || !NimbusConfig.get().enabled || !NimbusConfig.get().fluidCache) {
            this.nimbus$fluidBox = null;
            return;
        }
        if (this.nimbus$fluidBox != null && this.nimbus$fluidBox.equals(this.getBoundingBox()) && this.tickCount - this.nimbus$fluidTick < 10
                && this.tickCount >= this.nimbus$fluidTick) {
            cir.setReturnValue(this.nimbus$fluidResult);
        }
    }

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("RETURN"))
    private void nimbus$store(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && !self.isControlledByLocalInstance()) {
            this.nimbus$fluidBox = this.getBoundingBox();
            this.nimbus$fluidTick = this.tickCount;
            this.nimbus$fluidResult = cir.getReturnValueZ();
        }
    }
}

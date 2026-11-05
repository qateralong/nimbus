package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A server level drops particles, so the block lookup for sprint particles is wasted there.
@Mixin(Entity.class)
public abstract class EntitySprintMixin {
    @Shadow
    public abstract Level level();

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void nimbus$serverSide(CallbackInfo ci) {
        if (!this.level().isClientSide) {
            ci.cancel();
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Cull;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void nimbus$tick(Entity entity, CallbackInfo ci) {
        if (!Cull.tickEntity(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickEntities", at = @At("RETURN"))
    private void nimbus$endTick(CallbackInfo ci) {
        Cull.endTick();
    }
}

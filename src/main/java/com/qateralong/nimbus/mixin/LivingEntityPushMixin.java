package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPushMixin {
    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void nimbus$noPlayers(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ClientLevel level) || !NimbusConfig.get().enabled) {
            return;
        }
        AABB box = self.getBoundingBox();
        for (AbstractClientPlayer player : level.players()) {
            if (player != self && player.getBoundingBox().intersects(box)) {
                return;
            }
        }
        ci.cancel();
    }
}

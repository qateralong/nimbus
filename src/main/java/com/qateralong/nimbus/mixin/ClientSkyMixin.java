package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.SkySampler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientSkyMixin {
    @WrapOperation(method = "getSkyColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 nimbus$sky(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original) {
        if (!NimbusConfig.get().enabled || !NimbusConfig.get().biomeCache) {
            return original.call(pos, fetcher);
        }
        return SkySampler.sample(this, pos, fetcher);
    }
}

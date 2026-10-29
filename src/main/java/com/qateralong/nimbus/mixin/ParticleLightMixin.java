package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class ParticleLightMixin {
    @Unique
    private int nimbus$lightTick = Integer.MIN_VALUE;
    @Unique
    private int nimbus$light;

    @WrapMethod(method = "getLightColor")
    private int nimbus$cached(float partialTick, Operation<Integer> original) {
        int tick = Nimbus.ticks;
        if (this.nimbus$lightTick == tick) {
            return this.nimbus$light;
        }
        int light = original.call(partialTick);
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.particleLightCache) {
            this.nimbus$light = light;
            this.nimbus$lightTick = tick;
        }
        return light;
    }
}

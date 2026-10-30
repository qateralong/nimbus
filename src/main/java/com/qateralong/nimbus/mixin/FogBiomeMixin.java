package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.QuartBox;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class FogBiomeMixin {
    @WrapOperation(method = "method_24873", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getNoiseBiomeAtQuart(III)Lnet/minecraft/core/Holder;"), require = 0)
    private static Holder<Biome> nimbus$biome(BiomeManager manager, int x, int y, int z, Operation<Holder<Biome>> original) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.biomeCache) {
            return QuartBox.get(manager, x, y, z);
        }
        return original.call(manager, x, y, z);
    }
}

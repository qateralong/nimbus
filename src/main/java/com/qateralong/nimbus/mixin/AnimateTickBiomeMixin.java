package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public abstract class AnimateTickBiomeMixin {
    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> nimbus$biome(ClientLevel level, BlockPos pos) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.biomeCache) {
            return level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        }
        return level.getBiome(pos);
    }
}

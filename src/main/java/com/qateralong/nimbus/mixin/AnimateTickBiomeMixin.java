package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public abstract class AnimateTickBiomeMixin {
    @Unique
    private static final RandomSource NIMBUS_POSITIONS = new SingleThreadedRandomSource(RandomSupport.generateUniqueSeed());

    @Redirect(method = "doAnimateTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ClientLevel;random:Lnet/minecraft/util/RandomSource;"))
    private RandomSource nimbus$positions(ClientLevel level) {
        return NimbusConfig.get().enabled ? NIMBUS_POSITIONS : level.random;
    }

    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> nimbus$biome(ClientLevel level, BlockPos pos) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.biomeCache) {
            return level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        }
        return level.getBiome(pos);
    }

    @Redirect(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
    private RandomSource nimbus$random() {
        return NimbusConfig.get().enabled ? new SingleThreadedRandomSource(RandomSupport.generateUniqueSeed()) : RandomSource.create();
    }
}

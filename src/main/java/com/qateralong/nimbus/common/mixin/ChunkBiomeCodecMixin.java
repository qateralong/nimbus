package com.qateralong.nimbus.common.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The biome codec was built anew for every chunk read and written; it only depends on the registry.
@Mixin(ChunkSerializer.class)
public abstract class ChunkBiomeCodecMixin {
    @Unique
    private static volatile Object[] nimbus$last;

    @Inject(method = "makeBiomeCodec", at = @At("HEAD"), cancellable = true)
    private static void nimbus$cached(Registry<Biome> registry, CallbackInfoReturnable<Codec<PalettedContainerRO<Holder<Biome>>>> cir) {
        Object[] last = nimbus$last;
        if (last != null && last[0] == registry) {
            @SuppressWarnings("unchecked")
            Codec<PalettedContainerRO<Holder<Biome>>> codec = (Codec<PalettedContainerRO<Holder<Biome>>>) last[1];
            cir.setReturnValue(codec);
        }
    }

    @Inject(method = "makeBiomeCodec", at = @At("RETURN"))
    private static void nimbus$remember(Registry<Biome> registry, CallbackInfoReturnable<Codec<PalettedContainerRO<Holder<Biome>>>> cir) {
        nimbus$last = new Object[] {registry, cir.getReturnValue()};
    }
}

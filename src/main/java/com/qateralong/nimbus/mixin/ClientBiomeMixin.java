package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.BiomeCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientBiomeMixin implements LevelReader {
    @Unique
    private Holder<Biome> nimbus$plains;

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void nimbus$plains(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (this.nimbus$plains != null) {
            cir.setReturnValue(this.nimbus$plains);
        }
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("RETURN"))
    private void nimbus$storePlains(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        this.nimbus$plains = cir.getReturnValue();
    }
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        boolean cache = BiomeCache.usable();
        if (cache) {
            Holder<Biome> cached = BiomeCache.get(this, x, y, z);
            if (cached != null) {
                return cached;
            }
        }
        ChunkAccess chunk = this.getChunk(QuartPos.toSection(x), QuartPos.toSection(z), ChunkStatus.BIOMES, false);
        Holder<Biome> biome = chunk != null ? chunk.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
        if (cache) {
            BiomeCache.put(x, y, z, biome);
        }
        return biome;
    }
}

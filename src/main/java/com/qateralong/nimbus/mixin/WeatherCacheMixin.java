package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class WeatherCacheMixin {
    @Unique
    private static final Long2ObjectOpenHashMap<Holder<Biome>> NIMBUS_BIOMES = new Long2ObjectOpenHashMap<>();
    @Unique
    private static final Long2ObjectOpenHashMap<Biome.Precipitation> NIMBUS_PRECIPITATION = new Long2ObjectOpenHashMap<>();
    @Unique
    private static final Long2IntOpenHashMap NIMBUS_HEIGHTS = new Long2IntOpenHashMap();
    @Unique
    private static final Long2IntOpenHashMap NIMBUS_LIGHT = new Long2IntOpenHashMap();
    @Unique
    private static int nimbus$tick = Integer.MIN_VALUE;
    @Unique
    private static Level nimbus$level;

    @Unique
    private static boolean nimbus$cache(Level level) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || !config.weatherCache) {
            return false;
        }
        if (nimbus$tick != Nimbus.ticks || nimbus$level != level) {
            nimbus$tick = Nimbus.ticks;
            nimbus$level = level;
            NIMBUS_BIOMES.clear();
            NIMBUS_PRECIPITATION.clear();
            NIMBUS_HEIGHTS.clear();
            NIMBUS_LIGHT.clear();
        }
        return true;
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> nimbus$biome(Level level, BlockPos pos, Operation<Holder<Biome>> original) {
        if (!nimbus$cache(level)) {
            return original.call(level, pos);
        }
        long key = pos.asLong();
        Holder<Biome> biome = NIMBUS_BIOMES.get(key);
        if (biome == null) {
            biome = original.call(level, pos);
            NIMBUS_BIOMES.put(key, biome);
        }
        return biome;
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int nimbus$height(Level level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
        if (!nimbus$cache(level)) {
            return original.call(level, type, x, z);
        }
        long key = (long) x << 32 | z & 0xFFFFFFFFL;
        if (NIMBUS_HEIGHTS.containsKey(key)) {
            return NIMBUS_HEIGHTS.get(key);
        }
        int height = original.call(level, type, x, z);
        NIMBUS_HEIGHTS.put(key, height);
        return height;
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation nimbus$precipitation(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original) {
        if (nimbus$level == null || nimbus$tick != Nimbus.ticks) {
            return original.call(biome, pos);
        }
        long key = pos.asLong();
        Biome.Precipitation value = NIMBUS_PRECIPITATION.get(key);
        if (value == null) {
            value = original.call(biome, pos);
            NIMBUS_PRECIPITATION.put(key, value);
        }
        return value;
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int nimbus$light(BlockAndTintGetter level, BlockPos pos, Operation<Integer> original) {
        if (nimbus$level == null || nimbus$tick != Nimbus.ticks) {
            return original.call(level, pos);
        }
        long key = pos.asLong();
        if (NIMBUS_LIGHT.containsKey(key)) {
            return NIMBUS_LIGHT.get(key);
        }
        int light = original.call(level, pos);
        NIMBUS_LIGHT.put(key, light);
        return light;
    }
}

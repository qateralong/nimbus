package com.qateralong.nimbus;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public final class BiomeCache {
    private static final int SIZE = 4096;
    private static final long[] KEYS = new long[SIZE];
    private static final int[] STAMPS = new int[SIZE];
    @SuppressWarnings("unchecked")
    private static final Holder<Biome>[] VALUES = new Holder[SIZE];
    private static Object owner;
    private static int epoch = 1;

    private BiomeCache() {
    }

    private static int ticks;

    public static void tick() {
        if (++ticks % 20 == 0) {
            epoch++;
        }
    }

    public static boolean usable() {
        NimbusConfig config = NimbusConfig.get();
        return config.enabled && config.biomeCache && RenderSystem.isOnRenderThread();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | (long) z & 0x3FFFFF;
    }

    private static int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 52) & (SIZE - 1);
    }

    public static Holder<Biome> get(Object level, int x, int y, int z) {
        if (level != owner) {
            owner = level;
            epoch++;
            return null;
        }
        long key = key(x, y, z);
        int slot = slot(key);
        return STAMPS[slot] == epoch && KEYS[slot] == key ? VALUES[slot] : null;
    }

    public static void put(int x, int y, int z, Holder<Biome> value) {
        long key = key(x, y, z);
        int slot = slot(key);
        KEYS[slot] = key;
        VALUES[slot] = value;
        STAMPS[slot] = epoch;
    }
}

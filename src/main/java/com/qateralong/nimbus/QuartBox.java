package com.qateralong.nimbus;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

public final class QuartBox {
    private static final int SIZE = 8;
    @SuppressWarnings("unchecked")
    private static final Holder<Biome>[] VALUES = new Holder[SIZE * SIZE * SIZE];
    private static final int[] STAMPS = new int[SIZE * SIZE * SIZE];
    private static Object owner;
    private static int originX;
    private static int originY;
    private static int originZ;
    private static int epoch = 1;
    private static int epochTick = Integer.MIN_VALUE;

    private QuartBox() {
    }

    public static Holder<Biome> get(BiomeManager manager, int x, int y, int z) {
        int tick = Nimbus.ticks;
        if (manager != owner || tick - epochTick >= 20 || tick < epochTick) {
            owner = manager;
            epochTick = tick;
            epoch++;
        }
        int dx = x - originX;
        int dy = y - originY;
        int dz = z - originZ;
        if ((dx | dy | dz) < 0 || dx >= SIZE || dy >= SIZE || dz >= SIZE) {
            originX = x - 1;
            originY = y - 1;
            originZ = z - 1;
            epoch++;
            dx = 1;
            dy = 1;
            dz = 1;
        }
        int slot = (dx * SIZE + dy) * SIZE + dz;
        if (STAMPS[slot] != epoch) {
            VALUES[slot] = manager.getNoiseBiomeAtQuart(x, y, z);
            STAMPS[slot] = epoch;
        }
        return VALUES[slot];
    }
}

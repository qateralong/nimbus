package com.qateralong.nimbus;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public final class QuadDedup {
    private static final ConcurrentHashMap<Key, int[]> POOL = new ConcurrentHashMap<>();
    private static volatile boolean baking;
    public static volatile int saved;

    private record Key(int[] data, int hash) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.hash == this.hash && Arrays.equals(key.data, this.data);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private QuadDedup() {
    }

    public static void begin() {
        POOL.clear();
        saved = 0;
        baking = true;
    }

    public static void end() {
        baking = false;
        POOL.clear();
    }

    public static int[] intern(int[] vertices) {
        if (!baking || vertices == null) {
            return vertices;
        }
        int[] existing = POOL.putIfAbsent(new Key(vertices, Arrays.hashCode(vertices)), vertices);
        if (existing != null) {
            saved++;
            return existing;
        }
        return vertices;
    }
}

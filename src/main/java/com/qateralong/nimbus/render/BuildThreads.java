package com.qateralong.nimbus.render;

// Vanilla runs one section build per logical core, which starves the render thread on small CPUs. The buffer pool
// stays vanilla-sized; how many builds run at once is decided per frame from the render distance, since the work
// to catch up grows with its square.
public final class BuildThreads {
    private static final int OVERRIDE = Integer.getInteger("nimbus.buildThreads", -1);

    private BuildThreads() {
    }

    public static int active(int pool, int distance) {
        if (OVERRIDE == 0) {
            return pool;
        }
        if (OVERRIDE > 0) {
            return Math.min(pool, OVERRIDE);
        }
        int cores = Runtime.getRuntime().availableProcessors();
        int base = Math.max(2, Math.min(10, cores / 2 - 1));
        int scaled = (int) Math.ceil(base * distance * distance / 256.0);
        return Math.max(1, Math.min(pool, Math.max(base, scaled)));
    }
}

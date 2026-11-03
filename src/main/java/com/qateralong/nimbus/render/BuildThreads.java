package com.qateralong.nimbus.render;

// Vanilla allows one section build per logical core, which starves the render thread on small CPUs.
public final class BuildThreads {
    private static final int OVERRIDE = Integer.getInteger("nimbus.buildThreads", -1);

    private BuildThreads() {
    }

    public static int count(int requested) {
        if (OVERRIDE == 0 || !com.qateralong.nimbus.NimbusConfig.get().enabled) {
            return requested;
        }
        if (OVERRIDE > 0) {
            return OVERRIDE;
        }
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(requested, Math.max(2, Math.min(10, cores / 2 - 1))));
    }
}

package com.qateralong.nimbus.render;

import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.state.BlockState;

// A section compile runs start to end on one thread, so its 18x18x18 window arrays are reused per thread.
public final class WindowPool {
    public static final int SIZE = 18;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    private static final ThreadLocal<WindowPool> POOL = ThreadLocal.withInitial(WindowPool::new);
    public final BlockState[] states = new BlockState[VOLUME];
    public final int[] ids = new int[4096];
    public final ColorResolver[] resolvers = new ColorResolver[3];
    public final int[][] tints = new int[3][VOLUME];

    private WindowPool() {
    }

    public static WindowPool get() {
        WindowPool pool = POOL.get();
        java.util.Arrays.fill(pool.resolvers, null);
        return pool;
    }
}

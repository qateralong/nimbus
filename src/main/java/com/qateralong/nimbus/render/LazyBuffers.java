package com.qateralong.nimbus.render;

// render thread: the view area is creating its sections, whose buffers get GL objects only on first use
public final class LazyBuffers {
    public static boolean creatingSections;

    private LazyBuffers() {
    }
}

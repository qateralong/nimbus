package com.qateralong.nimbus.render;

// Section buffers whose GL objects are created on first real use: while the view area creates its sections (render
// thread) and when a section build worker asks for a layer's buffer for the first time (no GL there)
public final class LazyBuffers {
    public static boolean creatingSections;
    private static final ThreadLocal<Boolean> OFF_THREAD = ThreadLocal.withInitial(() -> false);

    private LazyBuffers() {
    }

    public static boolean lazy() {
        return creatingSections || OFF_THREAD.get();
    }

    public static boolean offThread() {
        return OFF_THREAD.get();
    }

    static void offThread(boolean on) {
        OFF_THREAD.set(on);
    }
}

package com.qateralong.nimbus.render;

import org.lwjgl.glfw.GLFW;

// The FPS limit with even frame intervals: vanilla restarts the interval from the moment it woke up (every oversleep
// pushes the next frame later, and on Windows the wait itself is coarse), here frames are due on a fixed grid, the wait
// sleeps until about 1.5 ms before the deadline handling input, then spins the rest.
public final class FramePacer {
    private static final long SPIN = 1_500_000L;
    private static long next;
    private static int lastFps;

    private FramePacer() {
    }

    public static void limit(int fps) {
        long period = 1_000_000_000L / Math.max(1, fps);
        long now = System.nanoTime();
        if (fps != lastFps || next == 0L) {
            lastFps = fps;
            next = now;
        }
        next += period;
        // behind after a slow frame: no catching up with short frames
        if (next < now) {
            next = now;
            GLFW.glfwPollEvents();
            return;
        }
        long left;
        while ((left = next - System.nanoTime()) > SPIN) {
            GLFW.glfwWaitEventsTimeout((left - SPIN) / 1.0E9);
        }
        while (System.nanoTime() < next) {
            Thread.onSpinWait();
        }
        GLFW.glfwPollEvents();
    }
}

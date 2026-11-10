package com.qateralong.nimbus.pack;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

// -Dnimbus.gpuTimes: GPU time between marks of a frame (timestamp queries read a few frames later), averaged and logged every 5 s.
final class GpuTimes {
    static final boolean ON = System.getProperty("nimbus.gpuTimes") != null;
    private static final int FRAMES = 4;
    private static final int MARKS = 64;
    static final boolean PASSES = "passes".equals(System.getProperty("nimbus.gpuTimes"));
    private static final int[][] queries = new int[FRAMES][MARKS];
    private static final String[][] names = new String[FRAMES][MARKS];
    private static final int[] used = new int[FRAMES];
    private static final java.util.Map<String, double[]> totals = new java.util.LinkedHashMap<>();
    private static int frame;
    private static long lastLog;

    private GpuTimes() {
    }

    static void begin() {
        if (!ON) {
            return;
        }
        frame = (frame + 1) % FRAMES;
        // the slot about to be reused was written FRAMES frames ago
        int n = used[frame];
        for (int i = 1; i < n; i++) {
            long a = GL33.glGetQueryObjecti64(queries[frame][i - 1], GL15.GL_QUERY_RESULT);
            long b = GL33.glGetQueryObjecti64(queries[frame][i], GL15.GL_QUERY_RESULT);
            double[] t = totals.computeIfAbsent(names[frame][i], k -> new double[2]);
            t[0] += (b - a) / 1.0E6;
            t[1]++;
        }
        used[frame] = 0;
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000 && !totals.isEmpty()) {
            lastLog = now;
            StringBuilder s = new StringBuilder();
            for (var e : totals.entrySet()) {
                s.append(e.getKey()).append(String.format(java.util.Locale.ROOT, " %.2f ", e.getValue()[0] / Math.max(1, e.getValue()[1])));
            }
            com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus GPU ms: {}", s);
            totals.clear();
        }
        mark("start");
    }

    static void mark(String name) {
        if (!ON) {
            return;
        }
        int n = used[frame];
        if (n >= MARKS) {
            return;
        }
        if (queries[frame][n] == 0) {
            queries[frame][n] = GL15.glGenQueries();
        }
        GL33.glQueryCounter(queries[frame][n], GL33.GL_TIMESTAMP);
        names[frame][n] = name;
        used[frame] = n + 1;
    }
}

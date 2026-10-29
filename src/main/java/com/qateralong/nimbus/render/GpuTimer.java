package com.qateralong.nimbus.render;

import com.qateralong.nimbus.Nimbus;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public final class GpuTimer {
    private static final int FRAMES = 4;
    private static final int LAYERS = 5;
    private static final int POINTS = 2 + LAYERS * 2;
    private static final boolean ENABLED = System.getProperty("nimbus.bench") != null || Boolean.getBoolean("nimbus.gputimer");
    private static final boolean[] issued = new boolean[FRAMES * POINTS];
    private static final double[] layerSum = new double[LAYERS];
    private static final long[] quadSum = new long[LAYERS];
    private static int[] queries;
    private static int slot;
    private static double frameSum;
    private static int samples;
    private static int frames;
    private static long lastLog;
    public static boolean debugScreen;
    public static double terrainMs;
    public static double frameMs;
    public static final double[] layerMs = new double[LAYERS];
    public static final long[] layerQuads = new long[LAYERS];

    private GpuTimer() {
    }

    private static boolean active() {
        return ENABLED || debugScreen;
    }

    public static void frameStart() {
        mark(0);
    }

    public static void frameEnd() {
        mark(1);
    }

    private static int layer(RenderType type) {
        List<RenderType> layers = RenderType.chunkBufferLayers();
        int index = layers.indexOf(type);
        return index < LAYERS ? index : -1;
    }

    public static void layerStart(RenderType type) {
        int index = layer(type);
        if (index >= 0) {
            mark(2 + index * 2);
        }
    }

    public static void layerEnd(RenderType type) {
        int index = layer(type);
        if (index >= 0) {
            mark(3 + index * 2);
        }
    }

    public static void quads(int layer, long count) {
        if (active() && layer >= 0 && layer < LAYERS) {
            quadSum[layer] += count;
        }
    }

    private static void mark(int point) {
        if (!active()) {
            return;
        }
        if (queries == null) {
            queries = new int[FRAMES * POINTS];
            GL15.glGenQueries(queries);
        }
        int index = slot * POINTS + point;
        GL33.glQueryCounter(queries[index], GL33.GL_TIMESTAMP);
        issued[index] = true;
    }

    private static boolean ready(int index) {
        return issued[index] && GL15.glGetQueryObjecti(queries[index], GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    private static long value(int index) {
        return GL33.glGetQueryObjecti64(queries[index], GL15.GL_QUERY_RESULT);
    }

    public static void endFrame() {
        debugScreen = net.minecraft.client.Minecraft.getInstance().getDebugOverlay().showDebugScreen();
        if (!active() || queries == null) {
            return;
        }
        frames++;
        slot = (slot + 1) % FRAMES;
        int base = slot * POINTS;
        if (ready(base) && ready(base + 1)) {
            frameSum += (value(base + 1) - value(base)) / 1e6;
            for (int layer = 0; layer < LAYERS; layer++) {
                int start = base + 2 + layer * 2;
                if (ready(start) && ready(start + 1)) {
                    layerSum[layer] += (value(start + 1) - value(start)) / 1e6;
                }
            }
            samples++;
        }
        for (int point = 0; point < POINTS; point++) {
            issued[base + point] = false;
        }
        long now = System.currentTimeMillis();
        if (now - lastLog > (ENABLED ? 5000 : 500) && samples > 0) {
            frameMs = frameSum / samples;
            double terrain = 0;
            for (int layer = 0; layer < LAYERS; layer++) {
                layerMs[layer] = layerSum[layer] / samples;
                layerQuads[layer] = quadSum[layer] / Math.max(1, frames);
                terrain += layer == layer(RenderType.translucent()) ? 0 : layerMs[layer];
                layerSum[layer] = 0;
                quadSum[layer] = 0;
            }
            terrainMs = terrain;
            if (ENABLED) {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "Nimbus GPU: frame %.3f ms, opaque terrain %.3f ms;", frameMs, terrainMs));
                List<RenderType> layers = RenderType.chunkBufferLayers();
                for (int layer = 0; layer < LAYERS && layer < layers.size(); layer++) {
                    line.append(String.format(Locale.ROOT, " %s %.3f ms/%dk quads", name(layers.get(layer)), layerMs[layer], layerQuads[layer] / 1000));
                }
                Nimbus.LOGGER.info(line.toString());
            }
            frameSum = 0;
            samples = 0;
            frames = 0;
            lastLog = now;
        }
    }

    private static String name(RenderType type) {
        String text = type.toString();
        int open = text.indexOf('[');
        int close = text.indexOf(':');
        return open >= 0 && close > open ? text.substring(open + 1, close) : text;
    }
}

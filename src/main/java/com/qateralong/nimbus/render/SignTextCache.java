package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

// Sign lines are laid out glyph by glyph every frame. A line's vertices are recorded once in text space and replayed
// through the current pose; the key is the line object itself, which the sign keeps until its text changes.
public final class SignTextCache {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Map<FormattedCharSequence, Entry> ENTRIES = new WeakHashMap<>();
    private static final Recorder RECORDER = new Recorder();

    private SignTextCache() {
    }

    public interface Draw {
        void run(Matrix4f pose, MultiBufferSource buffer, int light);
    }

    // kind separates plain text from the glowing outline variant drawn for the same line
    public static boolean replay(FormattedCharSequence text, int kind, float x, float y, int color, int extra, Matrix4f pose, MultiBufferSource buffer,
                                 int light, Draw draw) {
        Entry entry = ENTRIES.get(text);
        if (entry == null || entry.kind != kind || entry.x != x || entry.y != y || entry.color != color || entry.extra != extra) {
            if (obfuscated(text)) {
                return false;
            }
            entry = new Entry(kind, x, y, color, extra);
            RECORDER.begin(entry);
            draw.run(IDENTITY, RECORDER, 0);
            RECORDER.end();
            ENTRIES.put(text, entry);
        }
        for (Run run : entry.runs) {
            VertexConsumer consumer = buffer.getBuffer(run.type);
            float[] values = run.values;
            int[] colors = run.colors;
            for (int v = 0, n = colors.length; v < n; v++) {
                consumer.addVertex(pose, values[v * 5], values[v * 5 + 1], values[v * 5 + 2]).setColor(colors[v])
                        .setUv(values[v * 5 + 3], values[v * 5 + 4]).setLight(light);
            }
        }
        return true;
    }

    private static boolean obfuscated(FormattedCharSequence text) {
        boolean[] found = new boolean[1];
        text.accept((index, style, codePoint) -> {
            if (style.isObfuscated()) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    private static final class Entry {
        final int kind;
        final float x;
        final float y;
        final int color;
        final int extra;
        final List<Run> runs = new ArrayList<>(2);

        Entry(int kind, float x, float y, int color, int extra) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.color = color;
            this.extra = extra;
        }
    }

    private static final class Run {
        final RenderType type;
        float[] values = new float[5 * 64];
        int[] colors = new int[64];
        int count;

        Run(RenderType type) {
            this.type = type;
        }

        void trim() {
            this.values = java.util.Arrays.copyOf(this.values, this.count * 5);
            this.colors = java.util.Arrays.copyOf(this.colors, this.count);
        }
    }

    private static final class Recorder implements MultiBufferSource, VertexConsumer {
        private Entry entry;
        private Run run;

        void begin(Entry entry) {
            this.entry = entry;
            this.run = null;
        }

        void end() {
            for (Run run : this.entry.runs) {
                run.trim();
            }
            this.entry = null;
            this.run = null;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            for (Run run : this.entry.runs) {
                if (run.type == type) {
                    this.run = run;
                    return this;
                }
            }
            this.run = new Run(type);
            this.entry.runs.add(this.run);
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            Run run = this.run;
            if (run.count == run.colors.length) {
                run.values = java.util.Arrays.copyOf(run.values, run.values.length * 2);
                run.colors = java.util.Arrays.copyOf(run.colors, run.colors.length * 2);
            }
            int at = run.count++ * 5;
            run.values[at] = x;
            run.values[at + 1] = y;
            run.values[at + 2] = z;
            run.colors[run.count - 1] = -1;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.run.colors[this.run.count - 1] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int at = (this.run.count - 1) * 5;
            this.run.values[at + 3] = u;
            this.run.values[at + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    // Font.DisplayMode is part of the key through `extra` together with the background or outline colour
    public static int extra(Font.DisplayMode mode, int value) {
        return value * 31 + mode.ordinal();
    }
}

package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.BitSet;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;

public final class Origins {
    private static final IntArrayList FREE = new IntArrayList();
    private static final BitSet DIRTY = new BitSet();
    static int[] values = new int[4 * 4096];
    // when each section first got geometry at its current position, ms since START; drives the chunk fade-in
    private static int[] appear = new int[4096];
    private static final BitSet SHOWN = new BitSet();
    private static final long START = net.minecraft.Util.getMillis();
    public static volatile int moves;
    private static int next;
    private static int buffer;
    private static int capacity;

    private Origins() {
    }

    public static synchronized int acquire() {
        int id = FREE.isEmpty() ? next++ : FREE.popInt();
        if (id * 4 + 4 > values.length) {
            values = java.util.Arrays.copyOf(values, Math.max(values.length * 2, id * 4 + 4));
        }
        if (id >= appear.length) {
            appear = java.util.Arrays.copyOf(appear, Math.max(appear.length * 2, id + 1));
        }
        SectionData.reserve(id);
        return id;
    }

    public static synchronized void release(int id) {
        values[id * 4 + 3] = 0;
        SHOWN.clear(id);
        SectionData.layers(id, 0);
        FREE.add(id);
    }

    public static synchronized void set(int id, int x, int y, int z) {
        values[id * 4] = x;
        values[id * 4 + 1] = y;
        values[id * 4 + 2] = z;
        SHOWN.clear(id);
        SectionData.origin(id, x, y, z);
        moves++;
        SectionData.version++;
        DIRTY.set(id);
    }

    static synchronized void mask(int id, int bits) {
        if (id * 4 + 3 < values.length) {
            values[id * 4 + 3] = bits;
        }
        // a rebuild after a block change keeps its time: only a new chunk (or a section moved to new ground) fades in
        if (bits != 0 && !SHOWN.get(id) && id < appear.length) {
            SHOWN.set(id);
            appear[id] = now();
            DIRTY.set(id);
        }
        SectionData.layers(id, bits);
    }

    static int now() {
        return (int) (net.minecraft.Util.getMillis() - START);
    }

    static synchronized int idCount() {
        return next;
    }

    public static int[] values() {
        return values;
    }

    static int buffer() {
        return buffer;
    }

    static synchronized void flush() {
        if (buffer == 0 || capacity < next) {
            int size = Math.max(4096, Integer.highestOneBit(Math.max(1, next) - 1) << 1);
            if (buffer != 0) {
                org.lwjgl.opengl.GL15.glDeleteBuffers(buffer);
            }
            buffer = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, buffer);
            GL44.glBufferStorage(GL31.GL_COPY_WRITE_BUFFER, (long) size * 16, GL44.GL_DYNAMIC_STORAGE_BIT);
            capacity = size;
            DIRTY.set(0, next);
        }
        if (DIRTY.isEmpty()) {
            return;
        }
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, buffer);
        long scratch = MemoryUtil.nmemAlloc(16L * Math.max(1, next));
        try {
            for (int start = DIRTY.nextSetBit(0); start >= 0 && start < next; ) {
                int end = DIRTY.nextClearBit(start);
                end = Math.min(end, next);
                for (int id = start; id < end; id++) {
                    long at = scratch + (long) (id - start) * 16;
                    MemoryUtil.memPutInt(at, values[id * 4]);
                    MemoryUtil.memPutInt(at + 4, values[id * 4 + 1]);
                    MemoryUtil.memPutInt(at + 8, values[id * 4 + 2]);
                    MemoryUtil.memPutInt(at + 12, id < appear.length ? appear[id] : 0);
                }
                GL15.nglBufferSubData(GL31.GL_COPY_WRITE_BUFFER, (long) start * 16, (long) (end - start) * 16, scratch);
                start = DIRTY.nextSetBit(end);
            }
        } finally {
            MemoryUtil.nmemFree(scratch);
        }
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        DIRTY.clear();
    }
}

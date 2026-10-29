package com.qateralong.nimbus.render;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

final class Commands {
    static final int STRIDE = 20;
    private ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * 1024);
    int count;
    long quads;
    long offset = -1;

    void clear() {
        this.count = 0;
        this.quads = 0;
        this.offset = -1;
    }

    void add(int start, int quads, int instance, int limit) {
        this.quads += quads;
        while (quads > 0) {
            int part = Math.min(quads, limit);
            if ((this.count + 1) * STRIDE > this.buffer.capacity()) {
                ByteBuffer grown = MemoryUtil.memAlloc(this.buffer.capacity() * 2);
                MemoryUtil.memCopy(MemoryUtil.memAddress(this.buffer), MemoryUtil.memAddress(grown), (long) this.count * STRIDE);
                MemoryUtil.memFree(this.buffer);
                this.buffer = grown;
            }
            long at = MemoryUtil.memAddress(this.buffer) + (long) this.count * STRIDE;
            MemoryUtil.memPutInt(at, part * 6);
            MemoryUtil.memPutInt(at + 4, 1);
            MemoryUtil.memPutInt(at + 8, 0);
            MemoryUtil.memPutInt(at + 12, start * 4);
            MemoryUtil.memPutInt(at + 16, instance);
            this.count++;
            quads -= part;
            start += part;
        }
    }

    void reverse() {
        long base = MemoryUtil.memAddress(this.buffer);
        long scratch = MemoryUtil.nmemAlloc(STRIDE);
        for (int a = 0, b = this.count - 1; a < b; a++, b--) {
            MemoryUtil.memCopy(base + (long) a * STRIDE, scratch, STRIDE);
            MemoryUtil.memCopy(base + (long) b * STRIDE, base + (long) a * STRIDE, STRIDE);
            MemoryUtil.memCopy(scratch, base + (long) b * STRIDE, STRIDE);
        }
        MemoryUtil.nmemFree(scratch);
    }

    void upload() {
        if (this.count == 0) {
            this.offset = -1;
            return;
        }
        this.buffer.clear();
        this.buffer.limit(this.count * STRIDE);
        this.offset = Stream.write(this.buffer, STRIDE);
    }
}

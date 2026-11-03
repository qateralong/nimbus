package com.qateralong.nimbus.render;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

final class Commands {
    static final int STRIDE = 20;
    private ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * 1024);
    private long address = MemoryUtil.memAddress(this.buffer);
    int count;
    long quads;
    long offset = -1;
    long offset2 = -1;

    void clear() {
        this.count = 0;
        this.quads = 0;
        this.offset = -1;
        this.offset2 = -1;
    }

    void add(int start, int quads, int instance, int limit) {
        this.quads += quads;
        while (quads > 0) {
            int part = Math.min(quads, limit);
            if ((this.count + 1) * STRIDE > this.buffer.capacity()) {
                ByteBuffer grown = MemoryUtil.memAlloc(this.buffer.capacity() * 2);
                MemoryUtil.memCopy(this.address, MemoryUtil.memAddress(grown), (long) this.count * STRIDE);
                MemoryUtil.memFree(this.buffer);
                this.buffer = grown;
                this.address = MemoryUtil.memAddress(grown);
            }
            long at = this.address + (long) this.count * STRIDE;
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

    int instance(int index) {
        return MemoryUtil.memGetInt(this.address + (long) index * STRIDE + 16);
    }

    int quads(int index) {
        return MemoryUtil.memGetInt(this.address + (long) index * STRIDE) / 6;
    }

    int first(int index) {
        return MemoryUtil.memGetInt(this.address + (long) index * STRIDE + 12) / 4;
    }

    // Cuts the draws of marked sections (instance = section id), keeping the order of the rest.
    void remove(int[] marks, int stamp) {
        long base = this.address;
        int kept = 0;
        for (int i = 0; i < this.count; i++) {
            long at = base + (long) i * STRIDE;
            int instance = MemoryUtil.memGetInt(at + 16);
            if (instance >= 0 && instance < marks.length && marks[instance] == stamp) {
                this.quads -= MemoryUtil.memGetInt(at) / 6;
                continue;
            }
            if (kept != i) {
                MemoryUtil.memCopy(at, base + (long) kept * STRIDE, STRIDE);
            }
            kept++;
        }
        this.count = kept;
    }

    // Replaces the run of draws with this instance by `with`, in place; false when absent but there is something to add.
    boolean splice(int instance, Commands with) {
        int first = -1;
        int found = 0;
        long removed = 0;
        for (int i = 0; i < this.count; i++) {
            if (MemoryUtil.memGetInt(this.address + (long) i * STRIDE + 16) == instance) {
                if (first < 0) {
                    first = i;
                }
                found++;
                removed += MemoryUtil.memGetInt(this.address + (long) i * STRIDE) / 6;
            } else if (first >= 0) {
                break;
            }
        }
        if (first < 0) {
            return with.count == 0;
        }
        int total = this.count - found + with.count;
        if (total * STRIDE > this.buffer.capacity()) {
            ByteBuffer grown = MemoryUtil.memAlloc(Math.max(total * STRIDE, this.buffer.capacity() * 2));
            MemoryUtil.memCopy(this.address, MemoryUtil.memAddress(grown), (long) this.count * STRIDE);
            MemoryUtil.memFree(this.buffer);
            this.buffer = grown;
            this.address = MemoryUtil.memAddress(grown);
        }
        long base = this.address;
        int tail = this.count - first - found;
        int from = first + found;
        int to = first + with.count;
        if (to > from) {
            for (int i = tail - 1; i >= 0; i--) {
                MemoryUtil.memCopy(base + (long) (from + i) * STRIDE, base + (long) (to + i) * STRIDE, STRIDE);
            }
        } else if (to < from) {
            for (int i = 0; i < tail; i++) {
                MemoryUtil.memCopy(base + (long) (from + i) * STRIDE, base + (long) (to + i) * STRIDE, STRIDE);
            }
        }
        this.quads -= removed;
        MemoryUtil.memCopy(with.address, base + (long) first * STRIDE, (long) with.count * STRIDE);
        this.quads += with.quads;
        this.count = total;
        return true;
    }

    void reverse() {
        long base = this.address;
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
        this.offset2 = -1;
    }

    void uploadSecond() {
        if (this.count == 0) {
            this.offset2 = -1;
            return;
        }
        this.buffer.clear();
        this.buffer.limit(this.count * STRIDE);
        this.offset2 = Stream.write(this.buffer, STRIDE);
    }
}

package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Map;
import java.util.TreeMap;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;

final class Arena {
    static final int QUAD_BYTES = 64;
    private final TreeMap<Integer, Integer> free = new TreeMap<>();
    private int buffer;
    private int capacity;
    private int top;
    private int used;

    Arena(int quads) {
        this.capacity = quads;
        this.buffer = create(quads);
    }

    private static int create(int quads) {
        int id = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, id);
        GL44.glBufferStorage(GL31.GL_COPY_WRITE_BUFFER, (long) quads * QUAD_BYTES, GL44.GL_DYNAMIC_STORAGE_BIT);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        return id;
    }

    int buffer() {
        return this.buffer;
    }

    int usedQuads() {
        return this.used;
    }

    int capacityQuads() {
        return this.capacity;
    }

    int allocate(int quads) {
        for (Map.Entry<Integer, Integer> entry : this.free.entrySet()) {
            int length = entry.getValue();
            if (length >= quads) {
                int start = entry.getKey();
                this.free.remove(start);
                if (length > quads) {
                    this.free.put(start + quads, length - quads);
                }
                this.used += quads;
                return start;
            }
        }
        if (this.top + quads > this.capacity) {
            this.grow(Math.max(this.capacity + this.capacity / 2, this.top + quads + 65536));
        }
        int start = this.top;
        this.top += quads;
        this.used += quads;
        return start;
    }

    void release(int start, int quads) {
        if (quads <= 0) {
            return;
        }
        this.used -= quads;
        Map.Entry<Integer, Integer> before = this.free.floorEntry(start);
        if (before != null && before.getKey() + before.getValue() == start) {
            start = before.getKey();
            quads += before.getValue();
            this.free.remove(start);
        }
        Integer after = this.free.get(start + quads);
        if (after != null) {
            this.free.remove(start + quads);
            quads += after;
        }
        if (start + quads == this.top) {
            this.top = start;
        } else {
            this.free.put(start, quads);
        }
    }

    void upload(int start, long address, int quads) {
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, this.buffer);
        GL15.nglBufferSubData(GL31.GL_COPY_WRITE_BUFFER, (long) start * QUAD_BYTES, (long) quads * QUAD_BYTES, address);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    private void grow(int quads) {
        int next = create(quads);
        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, this.buffer);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, next);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, (long) this.top * QUAD_BYTES);
        GlStateManager._glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        org.lwjgl.opengl.GL15.glDeleteBuffers(this.buffer);
        this.buffer = next;
        this.capacity = quads;
    }

    void close() {
        org.lwjgl.opengl.GL15.glDeleteBuffers(this.buffer);
        this.free.clear();
        this.top = 0;
        this.used = 0;
    }

    static long allocateCpu(int quads) {
        return MemoryUtil.nmemAlloc((long) quads * QUAD_BYTES);
    }
}

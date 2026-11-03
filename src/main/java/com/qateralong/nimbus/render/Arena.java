package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Map;
import java.util.TreeMap;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;

// Quad storage split into fixed 64 MB pages: growing adds a page instead of copying everything (which stalls
// for a second on shared-memory GPUs). Allocations never cross a page; the shader picks the page from gl_VertexID.
final class Arena {
    static final int QUAD_BYTES = 64;
    static final int PAGE_SHIFT = 20;
    static final int PAGE_QUADS = 1 << PAGE_SHIFT;
    static final int VERTEX_SHIFT = PAGE_SHIFT + 2;
    static final int LIMIT = 16;
    static int maxPages = 8;

    private final TreeMap<Integer, Integer> free = new TreeMap<>();
    private final java.util.TreeSet<Long> sizes = new java.util.TreeSet<>();
    private final int[] buffers = new int[LIMIT];
    private final int[] tops = new int[LIMIT];
    private int pages;
    private int used;

    Arena() {
        this.addPage();
    }

    private void addPage() {
        int id = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, id);
        GL44.glBufferStorage(GL31.GL_COPY_WRITE_BUFFER, (long) PAGE_QUADS * QUAD_BYTES, GL44.GL_DYNAMIC_STORAGE_BIT);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        this.buffers[this.pages] = id;
        this.tops[this.pages] = 0;
        this.pages++;
    }

    int pages() {
        return this.pages;
    }

    int buffer(int page) {
        return this.buffers[page < this.pages ? page : 0];
    }

    int usedQuads() {
        return this.used;
    }

    int capacityQuads() {
        return this.pages * PAGE_QUADS;
    }

    // Returns the global quad index, or -1 when every page is full.
    int allocate(int quads) {
        if (quads > PAGE_QUADS) {
            return -1;
        }
        Long fit = this.sizes.ceiling((long) quads << 32);
        if (fit != null) {
            int start = (int) (long) fit;
            int length = (int) (fit >>> 32);
            this.take(start, length);
            if (length > quads) {
                this.give(start + quads, length - quads);
            }
            this.used += quads;
            return start;
        }
        for (int page = 0; ; page++) {
            if (page == this.pages) {
                if (this.pages >= maxPages) {
                    return -1;
                }
                this.addPage();
            }
            if (this.tops[page] + quads <= PAGE_QUADS) {
                int start = (page << PAGE_SHIFT) + this.tops[page];
                this.tops[page] += quads;
                this.used += quads;
                return start;
            }
        }
    }

    void release(int start, int quads) {
        if (quads <= 0 || start < 0) {
            return;
        }
        this.used -= quads;
        int page = start >>> PAGE_SHIFT;
        Map.Entry<Integer, Integer> before = this.free.floorEntry(start);
        if (before != null && before.getKey() + before.getValue() == start && before.getKey() >>> PAGE_SHIFT == page) {
            this.take(before.getKey(), before.getValue());
            start = before.getKey();
            quads += before.getValue();
        }
        int end = start + quads;
        Integer after = (end & (PAGE_QUADS - 1)) == 0 ? null : this.free.get(end);
        if (after != null) {
            this.take(end, after);
            quads += after;
        }
        if (start + quads == (page << PAGE_SHIFT) + this.tops[page]) {
            this.tops[page] = start - (page << PAGE_SHIFT);
        } else {
            this.give(start, quads);
        }
    }

    private void give(int start, int length) {
        this.free.put(start, length);
        this.sizes.add((long) length << 32 | start & 0xFFFFFFFFL);
    }

    private void take(int start, int length) {
        this.free.remove(start);
        this.sizes.remove((long) length << 32 | start & 0xFFFFFFFFL);
    }

    void upload(int start, long address, int quads) {
        if (start < 0) {
            return;
        }
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, this.buffers[start >>> PAGE_SHIFT]);
        GL15.nglBufferSubData(GL31.GL_COPY_WRITE_BUFFER, (long) (start & (PAGE_QUADS - 1)) * QUAD_BYTES, (long) quads * QUAD_BYTES, address);
        GlStateManager._glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    void close() {
        for (int i = 0; i < this.pages; i++) {
            GL15.glDeleteBuffers(this.buffers[i]);
        }
        this.pages = 0;
        this.free.clear();
        this.sizes.clear();
        this.used = 0;
    }

    static long allocateCpu(int quads) {
        return MemoryUtil.nmemAlloc((long) quads * QUAD_BYTES);
    }

    static String shaderPages(int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append("layout(std430, binding = ").append(i).append(") readonly buffer Page").append(i).append(" { uvec4 page").append(i).append("[]; };\n");
        }
        out.append("uvec4 fetchVertex(int i) {\n    int l = i & ").append((1 << VERTEX_SHIFT) - 1).append(";\n    switch (i >> ").append(VERTEX_SHIFT).append(") {\n");
        for (int i = 0; i < count; i++) {
            out.append(i == count - 1 ? "        default" : "        case " + i).append(": return page").append(i).append("[l];\n");
        }
        return out.append("    }\n}\n").toString();
    }
}

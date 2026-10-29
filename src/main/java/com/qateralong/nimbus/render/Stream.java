package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.Nimbus;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

public final class Stream {
    private static final long CAPACITY = 16L << 20;
    private static final ArrayDeque<long[]> FENCES = new ArrayDeque<>();
    private static int buffer;
    private static long address;
    private static long head;
    private static long completed;
    private static int state;

    private Stream() {
    }

    public static boolean available() {
        if (state == 0) {
            state = init() ? 1 : -1;
        }
        return state == 1;
    }

    private static boolean init() {
        try {
            GLCapabilities caps = GL.getCapabilities();
            if (!(caps.OpenGL44 || caps.GL_ARB_buffer_storage) || !caps.OpenGL32) {
                return false;
            }
            buffer = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            int flags = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
            GL44.glBufferStorage(GL15.GL_ARRAY_BUFFER, CAPACITY, flags);
            ByteBuffer mapped = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, CAPACITY, flags);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (mapped == null) {
                return false;
            }
            address = MemoryUtil.memAddress(mapped);
            Nimbus.LOGGER.info("Nimbus stream buffer: {} MB persistent mapped", CAPACITY >> 20);
            return true;
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Nimbus stream buffer unavailable", t);
            return false;
        }
    }

    public static int buffer() {
        return buffer;
    }

    public static long write(ByteBuffer data, int stride) {
        int size = data.remaining();
        if (size == 0 || size > CAPACITY / 4) {
            return -1;
        }
        long position = head % CAPACITY;
        long offset = (position + stride - 1) / stride * stride;
        long start;
        if (offset + size > CAPACITY) {
            start = head + CAPACITY - position;
            offset = 0;
        } else {
            start = head + offset - position;
        }
        long end = start + size;
        reserve(end);
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), address + offset, size);
        head = end;
        return offset;
    }

    private static void reserve(long end) {
        long needed = end - CAPACITY;
        while (completed < needed) {
            if (FENCES.isEmpty()) {
                fence();
            }
            long[] oldest = FENCES.poll();
            GL32.glClientWaitSync(oldest[0], GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
            GL32.glDeleteSync(oldest[0]);
            completed = oldest[1];
        }
    }

    private static void fence() {
        FENCES.add(new long[]{GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0), head});
    }

    public static void endFrame() {
        if (state != 1) {
            return;
        }
        if (FENCES.isEmpty() || FENCES.peekLast()[1] != head) {
            fence();
        }
        while (!FENCES.isEmpty()) {
            long[] oldest = FENCES.peek();
            int status = GL32.glGetSynci(oldest[0], GL32.GL_SYNC_STATUS, null);
            if (status != GL32.GL_SIGNALED) {
                break;
            }
            FENCES.poll();
            GL32.glDeleteSync(oldest[0]);
            completed = oldest[1];
        }
    }
}

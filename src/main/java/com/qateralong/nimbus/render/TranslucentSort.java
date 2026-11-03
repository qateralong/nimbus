package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.Nimbus;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import org.lwjgl.system.MemoryUtil;

// Back-to-front quad sorting of translucent sections on worker threads, straight from the kept packed copy:
// the main thread only uploads the finished block. Copies in flight are freed once their sort returns.
public final class TranslucentSort {
    private static final int MAX_IN_FLIGHT = 64;
    private static final IdentityHashMap<TerrainSlot, Long> PENDING = new IdentityHashMap<>();
    private static final LongOpenHashSet IN_FLIGHT = new LongOpenHashSet();
    private static final LongOpenHashSet RETIRED = new LongOpenHashSet();
    private static final ConcurrentLinkedQueue<Runnable> DONE = new ConcurrentLinkedQueue<>();

    private TranslucentSort() {
    }

    // Main thread. False when the slot has nothing to sort, is already queued, or the queue is full.
    public static boolean schedule(TerrainSlot slot, BlockPos origin, double x, double y, double z) {
        long copy = slot.nimbus$copy();
        int quads = slot.nimbus$count();
        if (copy == 0L || quads < 2 || PENDING.containsKey(slot) || PENDING.size() >= MAX_IN_FLIGHT) {
            return false;
        }
        PENDING.put(slot, copy);
        IN_FLIGHT.add(copy);
        float cx = (float) (x - origin.getX());
        float cy = (float) (y - origin.getY());
        float cz = (float) (z - origin.getZ());
        Util.backgroundExecutor().execute(() -> {
            long sorted = 0L;
            try {
                sorted = sort(copy, quads, cx, cy, cz);
            } catch (Throwable t) {
                Nimbus.LOGGER.error("Nimbus translucent sort failed", t);
            }
            long result = sorted;
            DONE.add(() -> finish(slot, copy, quads, result));
        });
        return true;
    }

    public static boolean pending(TerrainSlot slot) {
        return PENDING.containsKey(slot);
    }

    // Main thread, once per frame.
    public static void drain() {
        for (Runnable task; (task = DONE.poll()) != null; ) {
            task.run();
        }
    }

    // Main thread: frees a packed copy now, or after the sort that is reading it finishes.
    public static void retire(long copy) {
        if (copy == 0L) {
            return;
        }
        if (IN_FLIGHT.contains(copy)) {
            RETIRED.add(copy);
        } else {
            MemoryUtil.nmemFree(copy);
        }
    }

    private static void finish(TerrainSlot slot, long copy, int quads, long sorted) {
        PENDING.remove(slot);
        IN_FLIGHT.remove(copy);
        boolean retired = RETIRED.remove(copy);
        if (sorted != 0L) {
            if (!retired && slot.nimbus$copy() == copy && slot.nimbus$count() == quads && !((VertexBuffer) slot).isInvalid()) {
                Terrain.uploadSorted(slot, sorted, quads);
            }
            MemoryUtil.nmemFree(sorted);
        }
        if (retired) {
            MemoryUtil.nmemFree(copy);
        }
    }

    // Same key as vanilla VertexSorting.byDistance: squared distance from the camera to the quad centroid, farthest first.
    static long sort(long data, int quads, float cx, float cy, float cz) {
        long[] keys = new long[quads];
        for (int q = 0; q < quads; q++) {
            long at = data + (long) q * Arena.QUAD_BYTES;
            int sx = 0;
            int sy = 0;
            int sz = 0;
            for (int v = 0; v < 4; v++) {
                int a = MemoryUtil.memGetInt(at + v * 16L);
                sx += a & 0xFFFF;
                sy += a >>> 16;
                sz += MemoryUtil.memGetInt(at + v * 16L + 4) & 0xFFFF;
            }
            float dx = sx * (1.0F / 4096.0F) - 16.0F - cx;
            float dy = sy * (1.0F / 4096.0F) - 16.0F - cy;
            float dz = sz * (1.0F / 4096.0F) - 16.0F - cz;
            int bits = Float.floatToRawIntBits(dx * dx + dy * dy + dz * dz);
            keys[q] = (long) (Integer.MAX_VALUE - bits) << 32 | q;
        }
        Arrays.sort(keys);
        long out = Arena.allocateCpu(quads);
        for (int i = 0; i < quads; i++) {
            MemoryUtil.memCopy(data + (keys[i] & 0xFFFFFFFFL) * Arena.QUAD_BYTES, out + (long) i * Arena.QUAD_BYTES, Arena.QUAD_BYTES);
        }
        return out;
    }
}

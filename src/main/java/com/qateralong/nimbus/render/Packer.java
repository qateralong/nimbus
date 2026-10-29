package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

final class Packer {
    private static final int SOURCE_STRIDE = 32;
    private static final int TARGET_STRIDE = 16;

    private Packer() {
    }

    static boolean supported(MeshData mesh) {
        MeshData.DrawState state = mesh.drawState();
        return state.mode() == VertexFormat.Mode.QUADS && state.format() == DefaultVertexFormat.BLOCK && state.vertexCount() % 4 == 0;
    }

    static long pack(MeshData mesh) {
        int quads = mesh.drawState().vertexCount() / 4;
        long source = MemoryUtil.memAddress(mesh.vertexBuffer());
        long target = Arena.allocateCpu(quads);
        for (int vertex = 0, count = quads * 4; vertex < count; vertex++) {
            long s = source + (long) vertex * SOURCE_STRIDE;
            long t = target + (long) vertex * TARGET_STRIDE;
            int x = position(MemoryUtil.memGetFloat(s));
            int y = position(MemoryUtil.memGetFloat(s + 4));
            int z = position(MemoryUtil.memGetFloat(s + 8));
            int color = MemoryUtil.memGetInt(s + 12);
            int u = texture(MemoryUtil.memGetFloat(s + 16));
            int v = texture(MemoryUtil.memGetFloat(s + 20));
            int light = MemoryUtil.memGetInt(s + 24);
            int block = Math.min(255, light & 0xFFFF);
            int sky = Math.min(255, light >>> 16);
            MemoryUtil.memPutInt(t, x | y << 16);
            MemoryUtil.memPutInt(t + 4, z | u << 16);
            MemoryUtil.memPutInt(t + 8, v | block << 16 | sky << 24);
            MemoryUtil.memPutInt(t + 12, color);
        }
        return target;
    }

    static Faces group(MeshData mesh, long packed, int quads, long[] out) {
        long source = MemoryUtil.memAddress(mesh.vertexBuffer());
        int[] groups = new int[quads];
        int[] counts = new int[Faces.GROUPS];
        float[] planes = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] x = new float[4];
        float[] y = new float[4];
        float[] z = new float[4];
        for (int quad = 0; quad < quads; quad++) {
            long base = source + (long) quad * 4 * SOURCE_STRIDE;
            for (int corner = 0; corner < 4; corner++) {
                long at = base + (long) corner * SOURCE_STRIDE;
                x[corner] = MemoryUtil.memGetFloat(at);
                y[corner] = MemoryUtil.memGetFloat(at + 4);
                z[corner] = MemoryUtil.memGetFloat(at + 8);
            }
            int group = PackedBuilder.direction(x, y, z);
            if (group != Faces.UNASSIGNED) {
                float plane = group < 2 ? y[0] : group < 4 ? z[0] : x[0];
                boolean positive = group == 1 || group == 3 || group == 5;
                planes[group] = positive ? Math.min(planes[group], plane) : Math.max(planes[group], plane);
            }
            groups[quad] = group;
            counts[group]++;
        }
        int[] offsets = new int[Faces.GROUPS + 1];
        for (int group = 0; group < Faces.GROUPS; group++) {
            offsets[group + 1] = offsets[group] + counts[group];
        }
        int[] cursor = offsets.clone();
        long sorted = Arena.allocateCpu(quads);
        for (int quad = 0; quad < quads; quad++) {
            MemoryUtil.memCopy(packed + (long) quad * Arena.QUAD_BYTES, sorted + (long) cursor[groups[quad]]++ * Arena.QUAD_BYTES, Arena.QUAD_BYTES);
        }
        out[0] = sorted;
        return new Faces(offsets, planes);
    }

    static int[] order(ByteBuffer indices, VertexFormat.IndexType type, int quads) {
        if (indices == null) {
            return null;
        }
        int[] order = new int[quads];
        long address = MemoryUtil.memAddress(indices);
        boolean shorts = type == VertexFormat.IndexType.SHORT;
        int available = indices.remaining() / (shorts ? 2 : 4) / 6;
        for (int quad = 0; quad < quads; quad++) {
            if (quad >= available) {
                order[quad] = quad;
                continue;
            }
            long offset = (long) quad * 6 * (shorts ? 2 : 4);
            int first = shorts ? MemoryUtil.memGetShort(address + offset) & 0xFFFF : MemoryUtil.memGetInt(address + offset);
            order[quad] = Math.min(quads - 1, first >>> 2);
        }
        return order;
    }

    static long arrange(long original, int quads, int[] order) {
        if (order == null) {
            return original;
        }
        long sorted = Arena.allocateCpu(quads);
        for (int quad = 0; quad < quads; quad++) {
            MemoryUtil.memCopy(original + (long) order[quad] * Arena.QUAD_BYTES, sorted + (long) quad * Arena.QUAD_BYTES, Arena.QUAD_BYTES);
        }
        return sorted;
    }

    private static int position(float value) {
        return Math.max(0, Math.min(65535, Math.round((value + 16.0F) * 1024.0F)));
    }

    private static int texture(float value) {
        return Math.max(0, Math.min(65535, Math.round(value * 65536.0F)));
    }
}

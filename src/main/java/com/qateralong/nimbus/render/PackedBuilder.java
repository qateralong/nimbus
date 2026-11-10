package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public final class PackedBuilder extends BufferBuilder {
    private static final int QUAD = Arena.QUAD_BYTES;
    private final ByteBufferBuilder target;
    private final boolean translucent;
    private final long[] data = new long[Faces.GROUPS];
    private final int[] capacity = new int[Faces.GROUPS];
    private final int[] counts = new int[Faces.GROUPS];
    // negative-facing groups keep the highest plane (max), positive-facing the lowest (min)
    private final float[] planes = {-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE};
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];
    private final float[] u = new float[4];
    private final float[] v = new float[4];
    private final int[] color = new int[4];
    private final int[] light = new int[4];
    // shader pack mode: color word = tint rgb565 | ambient occlusion (7 bits) | block id index (9 bits)
    private final boolean pack = com.qateralong.nimbus.pack.BlockIds.on;
    private final float[] ao = {1.0F, 1.0F, 1.0F, 1.0F};
    private final Vector3f scratch = new Vector3f();
    private long[] sprites;
    private int corner;
    private boolean open;
    private boolean built;

    public PackedBuilder(ByteBufferBuilder target, boolean translucent) {
        super(target, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        this.target = target;
        this.translucent = translucent;
    }

    @Override
    public VertexConsumer addVertex(float px, float py, float pz) {
        this.finishVertex();
        int c = this.corner;
        this.x[c] = px;
        this.y[c] = py;
        this.z[c] = pz;
        this.color[c] = -1;
        this.ao[c] = 1.0F;
        this.u[c] = 0.0F;
        this.v[c] = 0.0F;
        this.light[c] = 0;
        this.open = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        if (this.open) {
            this.color[this.corner] = (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | r & 0xFF;
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    @Override
    public VertexConsumer setUv(float pu, float pv) {
        if (this.open) {
            this.u[this.corner] = pu;
            this.v[this.corner] = pv;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int pu, int pv) {
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int overlay) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int block, int sky) {
        if (this.open) {
            this.light[this.corner] = block & 0xFFFF | (sky & 0xFFFF) << 16;
        }
        return this;
    }

    @Override
    public VertexConsumer setLight(int packed) {
        return this.setUv2(packed & 0xFFFF, packed >>> 16 & 0xFFFF);
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
        return this;
    }

    @Override
    public void addVertex(float px, float py, float pz, int argb, float pu, float pv, int overlay, int packedLight, float nx, float ny, float nz) {
        this.addVertex(px, py, pz);
        this.setColor(argb);
        this.u[this.corner] = pu;
        this.v[this.corner] = pv;
        this.light[this.corner] = packedLight;
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] lightmap, int overlay, boolean readColor) {
        this.finishVertex();
        if (this.corner != 0) {
            this.corner = 0;
        }
        int[] vertices = quad.getVertices();
        int count = vertices.length / 8;
        if (count != 4) {
            super.putBulkData(pose, quad, brightness, r, g, b, a, lightmap, overlay, readColor);
            return;
        }
        Matrix4f matrix = pose.pose();
        int alpha = (int) (a * 255.0F);
        Vector3f position = this.scratch;
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            float cr;
            float cg;
            float cb;
            float shade = this.pack ? 1.0F : brightness[i];
            this.ao[i] = this.pack ? brightness[i] : 1.0F;
            if (readColor) {
                int vertexColor = vertices[o + 3];
                cr = (float) (vertexColor & 0xFF) * shade * r;
                cg = (float) (vertexColor >> 8 & 0xFF) * shade * g;
                cb = (float) (vertexColor >> 16 & 0xFF) * shade * b;
            } else {
                cr = shade * r * 255.0F;
                cg = shade * g * 255.0F;
                cb = shade * b * 255.0F;
            }
            matrix.transformPosition(Float.intBitsToFloat(vertices[o]), Float.intBitsToFloat(vertices[o + 1]), Float.intBitsToFloat(vertices[o + 2]), position);
            this.x[i] = position.x;
            this.y[i] = position.y;
            this.z[i] = position.z;
            this.color[i] = (alpha & 0xFF) << 24 | ((int) cb & 0xFF) << 16 | ((int) cg & 0xFF) << 8 | (int) cr & 0xFF;
            this.u[i] = Float.intBitsToFloat(vertices[o + 4]);
            this.v[i] = Float.intBitsToFloat(vertices[o + 5]);
            this.light[i] = lightmap[i];
        }
        this.emit(((AnimatedIndex) quad.getSprite()).nimbus$animIndex());
    }

    private void finishVertex() {
        if (!this.open) {
            return;
        }
        this.open = false;
        if (++this.corner == 4) {
            this.corner = 0;
            this.emit(AnimatedSprites.lookup((this.u[0] + this.u[1] + this.u[2] + this.u[3]) * 0.25F, (this.v[0] + this.v[1] + this.v[2] + this.v[3]) * 0.25F));
        }
    }

    private int group() {
        return direction(this.x, this.y, this.z);
    }

    static int direction(float[] x, float[] y, float[] z) {
        float ax = x[1] - x[0];
        float ay = y[1] - y[0];
        float az = z[1] - z[0];
        float bx = x[2] - x[0];
        float by = y[2] - y[0];
        float bz = z[2] - z[0];
        float cx = x[3] - x[0];
        float cy = y[3] - y[0];
        float cz = z[3] - z[0];
        float nx = ay * bz - az * by + by * cz - bz * cy;
        float ny = az * bx - ax * bz + bz * cx - bx * cz;
        float nz = ax * by - ay * bx + bx * cy - by * cx;
        if (flat(y)) {
            if (Math.abs(ny) > 1.0E-6F) {
                return ny < 0 ? 0 : 1;
            }
        } else if (flat(z)) {
            if (Math.abs(nz) > 1.0E-6F) {
                return nz < 0 ? 2 : 3;
            }
        } else if (flat(x)) {
            if (Math.abs(nx) > 1.0E-6F) {
                return nx < 0 ? 4 : 5;
            }
        }
        return Faces.UNASSIGNED;
    }

    private static boolean flat(float[] values) {
        float first = values[0];
        return Math.abs(values[1] - first) <= 1.0E-4F && Math.abs(values[2] - first) <= 1.0E-4F && Math.abs(values[3] - first) <= 1.0E-4F;
    }

    private void emit(int sprite) {
        int group = this.group();
        if (group != Faces.UNASSIGNED) {
            float plane = group < 2 ? this.y[0] : group < 4 ? this.z[0] : this.x[0];
            boolean positive = group == 1 || group == 3 || group == 5;
            this.planes[group] = positive ? Math.min(this.planes[group], plane) : Math.max(this.planes[group], plane);
        }
        int index = this.counts[group];
        if (index == this.capacity[group]) {
            int grown = Math.max(64, index * 2);
            this.data[group] = MemoryUtil.nmemRealloc(this.data[group], (long) grown * QUAD);
            this.capacity[group] = grown;
        }
        long at = this.data[group] + (long) index * QUAD;
        for (int i = 0; i < 4; i++) {
            long t = at + i * 16L;
            int block = Math.min(255, this.light[i] & 0xFFFF);
            int sky = Math.min(255, this.light[i] >>> 16);
            MemoryUtil.memPutInt(t, position(this.x[i]) | position(this.y[i]) << 16);
            MemoryUtil.memPutInt(t + 4, position(this.z[i]) | texture(this.u[i]) << 16);
            MemoryUtil.memPutInt(t + 8, texture(this.v[i]) | block << 16 | sky << 24);
            MemoryUtil.memPutInt(t + 12, this.pack ? packColor(this.color[i], this.ao[i], com.qateralong.nimbus.pack.BlockIds.index()) : this.color[i]);
        }
        this.counts[group] = index + 1;
        if (sprite >= 0) {
            if (this.sprites == null || this.sprites.length <= sprite >>> 6) {
                this.sprites = this.sprites == null ? new long[(sprite >>> 6) + 1] : java.util.Arrays.copyOf(this.sprites, (sprite >>> 6) + 1);
            }
            this.sprites[sprite >>> 6] |= 1L << (sprite & 63);
        }
    }

    // abgr color (r in the low byte) to rgb565 in the high half, occlusion and the block id index below
    private static int packColor(int abgr, float ao, int index) {
        int r = (abgr & 0xFF) >> 3;
        int g = (abgr >> 8 & 0xFF) >> 2;
        int b = (abgr >> 16 & 0xFF) >> 3;
        int occlusion = Math.max(0, Math.min(127, Math.round(ao * 127.0F)));
        return r << 27 | g << 21 | b << 16 | occlusion << 9 | index & 511;
    }

    private static int position(float value) {
        return Math.max(0, Math.min(65535, Math.round((value + 16.0F) * 1024.0F)));
    }

    private static int texture(float value) {
        return Math.max(0, Math.min(65535, Math.round(value * 65536.0F)));
    }

    @Override
    public MeshData build() {
        if (this.built) {
            return null;
        }
        this.built = true;
        this.finishVertex();
        try {
            int quads = 0;
            for (int count : this.counts) {
                quads += count;
            }
            if (quads == 0) {
                return null;
            }
            long out = this.target.reserve(quads * QUAD);
            int[] offsets = new int[Faces.GROUPS + 1];
            for (int group = 0; group < Faces.GROUPS; group++) {
                int count = this.counts[group];
                if (count > 0) {
                    MemoryUtil.memCopy(this.data[group], out + (long) offsets[group] * QUAD, (long) count * QUAD);
                }
                offsets[group + 1] = offsets[group] + count;
            }
            Vector3f[] sorted = null;
            if (this.translucent) {
                // vanilla's centroid (first and third corner), read back from the final group-ordered layout
                sorted = new Vector3f[quads];
                for (int i = 0; i < quads; i++) {
                    long q = out + (long) i * QUAD;
                    int a = MemoryUtil.memGetInt(q);
                    int b = MemoryUtil.memGetInt(q + 32);
                    int az = MemoryUtil.memGetInt(q + 4) & 0xFFFF;
                    int bz = MemoryUtil.memGetInt(q + 36) & 0xFFFF;
                    sorted[i] = new Vector3f(((a & 0xFFFF) + (b & 0xFFFF)) * (1.0F / 2048.0F) - 16.0F,
                            ((a >>> 16) + (b >>> 16)) * (1.0F / 2048.0F) - 16.0F, (az + bz) * (1.0F / 2048.0F) - 16.0F);
                }
            }
            ByteBufferBuilder.Result result = this.target.build();
            if (result == null) {
                return null;
            }
            MeshData mesh = new MeshData(result, new MeshData.DrawState(DefaultVertexFormat.BLOCK, quads * 4, quads * 6, VertexFormat.Mode.QUADS,
                    VertexFormat.IndexType.least(quads * 4)));
            ((PackedMesh) mesh).nimbus$setPacked(new Packed(new Faces(offsets, this.planes.clone()),
                    AnimatedSprites.indices(this.sprites), sorted));
            return mesh;
        } finally {
            for (int group = 0; group < Faces.GROUPS; group++) {
                if (this.data[group] != 0L) {
                    MemoryUtil.nmemFree(this.data[group]);
                    this.data[group] = 0L;
                }
            }
        }
    }

    @Override
    public MeshData buildOrThrow() {
        MeshData mesh = this.build();
        if (mesh == null) {
            throw new IllegalStateException("BufferBuilder was empty");
        }
        return mesh;
    }

    public static MeshData.SortState sort(MeshData mesh, VertexSorting sorting) {
        Packed packed = ((PackedMesh) mesh).nimbus$packed();
        packed.order = sorting.sort(packed.centroids);
        return new MeshData.SortState(packed.centroids, mesh.drawState().indexType());
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ModelPart.Cube.class)
public abstract class ModelCubeMixin implements com.qateralong.nimbus.render.EntityInstances.Cube {
    @Override
    public ModelPart.Polygon[] nimbus$polygons() {
        return this.polygons;
    }

    @Unique
    private static final float[] NIMBUS_SCRATCH = new float[3 * 64];

    @Unique
    private static final Vector3f NIMBUS_VECTOR = new Vector3f();

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private ModelPart.Polygon[] polygons;

    @Unique
    private float[] nimbus$corners;

    @Unique
    private int[] nimbus$indices;

    @Unique
    private void nimbus$prepare() {
        float[] corners = new float[3 * 64];
        int count = 0;
        int total = 0;
        for (ModelPart.Polygon polygon : this.polygons) {
            total += polygon.vertices.length;
        }
        int[] indices = new int[total];
        int at = 0;
        for (ModelPart.Polygon polygon : this.polygons) {
            for (ModelPart.Vertex vertex : polygon.vertices) {
                float x = vertex.pos.x() / 16.0F;
                float y = vertex.pos.y() / 16.0F;
                float z = vertex.pos.z() / 16.0F;
                int found = -1;
                for (int i = 0; i < count; i++) {
                    if (corners[i * 3] == x && corners[i * 3 + 1] == y && corners[i * 3 + 2] == z) {
                        found = i;
                        break;
                    }
                }
                if (found < 0) {
                    if (count == 64) {
                        this.nimbus$indices = new int[0];
                        return;
                    }
                    corners[count * 3] = x;
                    corners[count * 3 + 1] = y;
                    corners[count * 3 + 2] = z;
                    found = count++;
                }
                indices[at++] = found;
            }
        }
        this.nimbus$corners = java.util.Arrays.copyOf(corners, count * 3);
        this.nimbus$indices = indices;
    }

    /**
     * @author qateralong
     * @reason shared corners are transformed once per cube instead of once per polygon vertex, with no callback per cube
     */
    @org.spongepowered.asm.mixin.Overwrite
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        if (!NimbusConfig.get().enabled || !RenderSystem.isOnRenderThread()) {
            this.nimbus$vanilla(pose, consumer, light, overlay, color);
            return;
        }
        if (this.nimbus$indices == null) {
            this.nimbus$prepare();
        }
        float[] corners = this.nimbus$corners;
        if (corners == null) {
            this.nimbus$vanilla(pose, consumer, light, overlay, color);
            return;
        }
        Matrix4f matrix = pose.pose();
        float[] out = NIMBUS_SCRATCH;
        for (int i = 0, n = corners.length / 3; i < n; i++) {
            float x = corners[i * 3];
            float y = corners[i * 3 + 1];
            float z = corners[i * 3 + 2];
            out[i * 3] = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
            out[i * 3 + 1] = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
            out[i * 3 + 2] = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();
        }
        int[] indices = this.nimbus$indices;
        if (NIMBUS_LITTLE_ENDIAN && consumer instanceof com.qateralong.nimbus.render.FastVertices fast) {
            long base = fast.nimbus$reserve(com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, indices.length);
            if (base != 0L) {
                this.nimbus$write(base, pose, out, indices, light, overlay, color);
                return;
            }
        }
        int at = 0;
        Vector3f normal = NIMBUS_VECTOR;
        for (ModelPart.Polygon polygon : this.polygons) {
            pose.transformNormal(polygon.normal, normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            for (ModelPart.Vertex vertex : polygon.vertices) {
                int corner = indices[at++] * 3;
                consumer.addVertex(out[corner], out[corner + 1], out[corner + 2], color, vertex.u, vertex.v, overlay, light, nx, ny, nz);
            }
        }
    }

    @Unique
    private static final boolean NIMBUS_LITTLE_ENDIAN = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;

    // NEW_ENTITY layout: position 3f, colour ABGR, uv 2f, overlay 2s, light 2s, normal 3b + pad; 36 bytes
    @Unique
    private void nimbus$write(long base, PoseStack.Pose pose, float[] out, int[] indices, int light, int overlay, int color) {
        int abgr = net.minecraft.util.FastColor.ABGR32.fromArgb32(color);
        short overlayU = (short) (overlay & 0xFFFF);
        short overlayV = (short) (overlay >> 16 & 0xFFFF);
        short lightU = (short) (light & 0xFFFF);
        short lightV = (short) (light >> 16 & 0xFFFF);
        org.joml.Matrix3f m = pose.normal();
        float m00 = m.m00();
        float m01 = m.m01();
        float m02 = m.m02();
        float m10 = m.m10();
        float m11 = m.m11();
        float m12 = m.m12();
        float m20 = m.m20();
        float m21 = m.m21();
        float m22 = m.m22();
        boolean trusted = pose.trustedNormals;
        long p = base;
        int at = 0;
        for (ModelPart.Polygon polygon : this.polygons) {
            Vector3f source = polygon.normal;
            float px = source.x();
            float py = source.y();
            float pz = source.z();
            float tx = m00 * px + m10 * py + m20 * pz;
            float ty = m01 * px + m11 * py + m21 * pz;
            float tz = m02 * px + m12 * py + m22 * pz;
            if (!trusted) {
                float scale = org.joml.Math.invsqrt(tx * tx + ty * ty + tz * tz);
                tx *= scale;
                ty *= scale;
                tz *= scale;
            }
            byte nx = nimbus$normal(tx);
            byte ny = nimbus$normal(ty);
            byte nz = nimbus$normal(tz);
            for (ModelPart.Vertex vertex : polygon.vertices) {
                int corner = indices[at++] * 3;
                org.lwjgl.system.MemoryUtil.memPutFloat(p, out[corner]);
                org.lwjgl.system.MemoryUtil.memPutFloat(p + 4L, out[corner + 1]);
                org.lwjgl.system.MemoryUtil.memPutFloat(p + 8L, out[corner + 2]);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 12L, abgr);
                org.lwjgl.system.MemoryUtil.memPutFloat(p + 16L, vertex.u);
                org.lwjgl.system.MemoryUtil.memPutFloat(p + 20L, vertex.v);
                org.lwjgl.system.MemoryUtil.memPutShort(p + 24L, overlayU);
                org.lwjgl.system.MemoryUtil.memPutShort(p + 26L, overlayV);
                org.lwjgl.system.MemoryUtil.memPutShort(p + 28L, lightU);
                org.lwjgl.system.MemoryUtil.memPutShort(p + 30L, lightV);
                org.lwjgl.system.MemoryUtil.memPutByte(p + 32L, nx);
                org.lwjgl.system.MemoryUtil.memPutByte(p + 33L, ny);
                org.lwjgl.system.MemoryUtil.memPutByte(p + 34L, nz);
                p += 36L;
            }
        }
    }

    @Unique
    private static byte nimbus$normal(float value) {
        return (byte) ((int) (net.minecraft.util.Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 255);
    }

    @Unique
    private void nimbus$vanilla(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = pose.pose();
        Vector3f scratch = new Vector3f();
        for (ModelPart.Polygon polygon : this.polygons) {
            Vector3f normal = pose.transformNormal(polygon.normal, scratch);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            for (ModelPart.Vertex vertex : polygon.vertices) {
                Vector3f position = matrix.transformPosition(vertex.pos.x() / 16.0F, vertex.pos.y() / 16.0F, vertex.pos.z() / 16.0F, scratch);
                consumer.addVertex(position.x(), position.y(), position.z(), color, vertex.u, vertex.v, overlay, light, nx, ny, nz);
            }
        }
    }
}

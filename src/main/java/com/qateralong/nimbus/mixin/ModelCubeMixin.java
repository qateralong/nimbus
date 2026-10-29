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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public abstract class ModelCubeMixin {
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

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void nimbus$compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        if (!NimbusConfig.get().enabled || !RenderSystem.isOnRenderThread()) {
            return;
        }
        if (this.nimbus$indices == null) {
            this.nimbus$prepare();
        }
        float[] corners = this.nimbus$corners;
        if (corners == null) {
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
        ci.cancel();
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.FastVertices;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Billboard quads written straight into the particle buffer: one reserve per quad instead of four chained vertex calls.
@Mixin(SingleQuadParticle.class)
public abstract class ParticleQuadMixin extends Particle {
    @Unique
    private static final boolean NIMBUS_LITTLE_ENDIAN = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;

    @Unique
    private static final Vector3f NIMBUS_RIGHT = new Vector3f();

    @Unique
    private static final Vector3f NIMBUS_UP = new Vector3f();

    protected ParticleQuadMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Shadow
    public abstract float getQuadSize(float partialTicks);

    @Shadow
    protected abstract float getU0();

    @Shadow
    protected abstract float getU1();

    @Shadow
    protected abstract float getV0();

    @Shadow
    protected abstract float getV1();

    @Shadow
    protected abstract void renderVertex(VertexConsumer consumer, Quaternionf rotation, float x, float y, float z, float xOffset, float yOffset, float size,
                                         float u, float v, int light);

    /**
     * @author qateralong
     * @reason the four corners go into the buffer in one write when it is a plain particle builder
     */
    @Overwrite
    protected void renderRotatedQuad(VertexConsumer consumer, Quaternionf rotation, float x, float y, float z, float partialTicks) {
        float size = this.getQuadSize(partialTicks);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);
        if (NIMBUS_LITTLE_ENDIAN && consumer instanceof FastVertices fast && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            long p = fast.nimbus$reserve(DefaultVertexFormat.PARTICLE, 4);
            if (p != 0L) {
                Vector3f right = rotation.transformPositiveX(NIMBUS_RIGHT).mul(size);
                Vector3f up = rotation.transformPositiveY(NIMBUS_UP).mul(size);
                int abgr = ((int) (this.alpha * 255.0F) & 255) << 24 | ((int) (this.bCol * 255.0F) & 255) << 16
                        | ((int) (this.gCol * 255.0F) & 255) << 8 | (int) (this.rCol * 255.0F) & 255;
                short lightU = (short) (light & 0xFFFF);
                short lightV = (short) (light >> 16 & 0xFFFF);
                nimbus$corner(p, x, y, z, right, up, 1.0F, -1.0F, u1, v1, abgr, lightU, lightV);
                nimbus$corner(p + 28L, x, y, z, right, up, 1.0F, 1.0F, u1, v0, abgr, lightU, lightV);
                nimbus$corner(p + 56L, x, y, z, right, up, -1.0F, 1.0F, u0, v0, abgr, lightU, lightV);
                nimbus$corner(p + 84L, x, y, z, right, up, -1.0F, -1.0F, u0, v1, abgr, lightU, lightV);
                return;
            }
        }
        this.renderVertex(consumer, rotation, x, y, z, 1.0F, -1.0F, size, u1, v1, light);
        this.renderVertex(consumer, rotation, x, y, z, 1.0F, 1.0F, size, u1, v0, light);
        this.renderVertex(consumer, rotation, x, y, z, -1.0F, 1.0F, size, u0, v0, light);
        this.renderVertex(consumer, rotation, x, y, z, -1.0F, -1.0F, size, u0, v1, light);
    }

    // PARTICLE layout: position 3f, uv 2f, colour ABGR, light 2s; 28 bytes
    @Unique
    private static void nimbus$corner(long p, float x, float y, float z, Vector3f right, Vector3f up, float a, float b, float u, float v, int abgr,
                                      short lightU, short lightV) {
        MemoryUtil.memPutFloat(p, x + right.x * a + up.x * b);
        MemoryUtil.memPutFloat(p + 4L, y + right.y * a + up.y * b);
        MemoryUtil.memPutFloat(p + 8L, z + right.z * a + up.z * b);
        MemoryUtil.memPutFloat(p + 12L, u);
        MemoryUtil.memPutFloat(p + 16L, v);
        MemoryUtil.memPutInt(p + 20L, abgr);
        MemoryUtil.memPutShort(p + 24L, lightU);
        MemoryUtil.memPutShort(p + 26L, lightV);
    }
}

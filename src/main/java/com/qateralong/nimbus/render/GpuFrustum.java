package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import java.io.IOException;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;

// Per-draw frustum test on the GPU: the lists hold every section of the widened search, and each frame a compute pass
// zeroes the instance count of draws outside the real frustum. Turning the camera then needs no CPU list rebuild.
final class GpuFrustum {
    // a little slack so a section is never dropped by float error at the screen edge
    private static final float SLACK = 0.5F;
    private static boolean failed;
    private static int program;
    private static int base;
    private static int count;
    private static int planes;
    private static int block;
    private static int frac;
    private static final Matrix4f MVP = new Matrix4f();
    private static final Vector4f PLANE = new Vector4f();
    private static final float[] VALUES = new float[24];

    private GpuFrustum() {
    }

    static boolean available() {
        if (failed) {
            return false;
        }
        if (program != 0) {
            return true;
        }
        try {
            int shader = TerrainProgram.compile(GL43.GL_COMPUTE_SHADER, TerrainProgram.source("frustum_cull.csh"));
            int id = GL20.glCreateProgram();
            GL20.glAttachShader(id, shader);
            GL20.glLinkProgram(id);
            GL20.glDeleteShader(shader);
            if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
                throw new IOException("frustum program link failed: " + GL20.glGetProgramInfoLog(id));
            }
            base = GL20.glGetUniformLocation(id, "CommandBase");
            count = GL20.glGetUniformLocation(id, "CommandCount");
            planes = GL20.glGetUniformLocation(id, "Planes");
            block = GL20.glGetUniformLocation(id, "CameraBlock");
            frac = GL20.glGetUniformLocation(id, "CameraFrac");
            program = id;
            return true;
        } catch (Throwable t) {
            failed = true;
            Nimbus.LOGGER.error("Nimbus GPU frustum culling failed to start, culling on the CPU", t);
            return false;
        }
    }

    static void cull(Commands list, Matrix4f projection, Matrix4f modelView, double x, double y, double z) {
        if (list.count == 0 || list.offset < 0 || !available()) {
            return;
        }
        projection.mul(modelView, MVP);
        float[] values = VALUES;
        for (int i = 0; i < 6; i++) {
            MVP.frustumPlane(i, PLANE);
            values[i * 4] = PLANE.x;
            values[i * 4 + 1] = PLANE.y;
            values[i * 4 + 2] = PLANE.z;
            values[i * 4 + 3] = PLANE.w + SLACK;
        }
        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniform4fv(planes, stack.floats(values));
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        GL20.glUniform3i(block, bx, by, bz);
        GL20.glUniform3f(frac, (float) (x - bx), (float) (y - by), (float) (z - bz));
        GL30.glUniform1ui(base, (int) (list.offset >> 2));
        GL30.glUniform1ui(count, list.count);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, Stream.buffer());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, Origins.buffer());
        GL43.glDispatchCompute((list.count + 63) >> 6, 1, 1);
        GL42.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, 0);
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
    }
}

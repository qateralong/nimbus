package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import java.nio.ByteBuffer;
import net.minecraft.client.particle.ParticleRenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

// Standard billboard particles as one instance each (centre, rotation, size, uv, colour, light); the vertex shader
// builds the quad. Particles with their own render code keep writing vertices into vanilla's buffer.
public final class ParticleInstances {
    private static final int STRIDE = 48;
    private static final Matrix4f MVP = new Matrix4f();
    private static Object target;
    private static ByteBuffer data = MemoryUtil.memAlloc(STRIDE * 4096);
    private static int count;
    public static int drawn;
    private static int state;
    private static int program;
    private static int vao;
    private static int mvp;
    private static int fogShape;
    private static int colorModulator;
    private static int fogStart;
    private static int fogEnd;
    private static int fogColor;

    private ParticleInstances() {
    }

    private static boolean available() {
        if (state == 0) {
            state = init() ? 1 : -1;
        }
        return state == 1;
    }

    private static boolean init() {
        GLCapabilities caps = GL.getCapabilities();
        if (!(caps.OpenGL43 || caps.GL_ARB_vertex_attrib_binding) || !Stream.available()) {
            return false;
        }
        try {
            int vertex = TerrainProgram.compile(GL20.GL_VERTEX_SHADER, source("particle.vsh"));
            int fragment = TerrainProgram.compile(GL20.GL_FRAGMENT_SHADER, source("particle.fsh"));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                Nimbus.LOGGER.warn("Nimbus particle program link failed: {}", GL20.glGetProgramInfoLog(program));
                return false;
            }
            mvp = GL20.glGetUniformLocation(program, "MvpMat");
            fogShape = GL20.glGetUniformLocation(program, "FogShape");
            colorModulator = GL20.glGetUniformLocation(program, "ColorModulator");
            fogStart = GL20.glGetUniformLocation(program, "FogStart");
            fogEnd = GL20.glGetUniformLocation(program, "FogEnd");
            fogColor = GL20.glGetUniformLocation(program, "FogColor");
            GlStateManager._glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler2"), 2);
            GlStateManager._glUseProgram(0);
            ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
            ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
            vao = GL30.glGenVertexArrays();
            GlStateManager._glBindVertexArray(vao);
            GL20.glEnableVertexAttribArray(0);
            GL43.glVertexAttribFormat(0, 3, GL11.GL_FLOAT, false, 0);
            GL20.glEnableVertexAttribArray(1);
            GL43.glVertexAttribFormat(1, 4, GL11.GL_SHORT, true, 12);
            GL20.glEnableVertexAttribArray(2);
            GL43.glVertexAttribFormat(2, 1, GL11.GL_FLOAT, false, 20);
            GL20.glEnableVertexAttribArray(3);
            GL43.glVertexAttribFormat(3, 4, GL11.GL_FLOAT, false, 24);
            GL20.glEnableVertexAttribArray(4);
            GL43.glVertexAttribFormat(4, 4, GL11.GL_UNSIGNED_BYTE, true, 40);
            GL20.glEnableVertexAttribArray(5);
            GL43.glVertexAttribIFormat(5, 2, GL11.GL_SHORT, 44);
            for (int attribute = 0; attribute < 6; attribute++) {
                GL43.glVertexAttribBinding(attribute, 0);
            }
            GL43.glVertexBindingDivisor(0, 1);
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
            Nimbus.LOGGER.info("Nimbus instanced particles ready");
            return true;
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Nimbus instanced particles unavailable", t);
            return false;
        }
    }

    private static String source(String name) throws java.io.IOException {
        try (var stream = ParticleInstances.class.getResourceAsStream("/assets/nimbus/shaders/particle/" + name)) {
            if (stream == null) {
                throw new java.io.IOException("Missing shader " + name);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // vanilla sheets only: custom render types set up state the instanced draw does not know about
    public static void begin(ParticleRenderType type, Object builder) {
        target = null;
        count = 0;
        NimbusConfig config = NimbusConfig.get();
        if (builder == null || !config.enabled || !config.particleInstancing || com.qateralong.nimbus.Compat.IRIS) {
            return;
        }
        if (type != ParticleRenderType.PARTICLE_SHEET_OPAQUE && type != ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                && type != ParticleRenderType.PARTICLE_SHEET_LIT && type != ParticleRenderType.TERRAIN_SHEET) {
            return;
        }
        if (available()) {
            target = builder;
        }
    }

    public static boolean accepts(Object consumer) {
        return target != null && consumer == target;
    }

    public static void add(Quaternionf rotation, float x, float y, float z, float size, float u0, float u1, float v0, float v1, int abgr, int light) {
        if (data.capacity() < (count + 1) * STRIDE) {
            data = MemoryUtil.memRealloc(data, data.capacity() * 2);
        }
        long p = MemoryUtil.memAddress(data) + (long) count * STRIDE;
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4L, y);
        MemoryUtil.memPutFloat(p + 8L, z);
        MemoryUtil.memPutShort(p + 12L, snorm(rotation.x));
        MemoryUtil.memPutShort(p + 14L, snorm(rotation.y));
        MemoryUtil.memPutShort(p + 16L, snorm(rotation.z));
        MemoryUtil.memPutShort(p + 18L, snorm(rotation.w));
        MemoryUtil.memPutFloat(p + 20L, size);
        MemoryUtil.memPutFloat(p + 24L, u0);
        MemoryUtil.memPutFloat(p + 28L, v0);
        MemoryUtil.memPutFloat(p + 32L, u1);
        MemoryUtil.memPutFloat(p + 36L, v1);
        MemoryUtil.memPutInt(p + 40L, abgr);
        MemoryUtil.memPutInt(p + 44L, light);
        count++;
    }

    private static short snorm(float value) {
        return (short) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 32767.0F);
    }

    // drawn with the render type's blend/depth state still set, before the vertices of custom particles
    public static void flush() {
        target = null;
        if (count == 0) {
            return;
        }
        int instances = count;
        count = 0;
        drawn += instances;
        data.limit(instances * STRIDE).position(0);
        long offset = Stream.write(data, STRIDE);
        data.clear();
        if (offset < 0) {
            return;
        }
        int atlas = RenderSystem.getShaderTexture(0);
        int lightmap = RenderSystem.getShaderTexture(2);
        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(mvp, false, RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), MVP).get(stack.mallocFloat(16)));
        }
        float[] color = RenderSystem.getShaderColor();
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(colorModulator, color[0], color[1], color[2], color[3]);
        GL20.glUniform4f(fogColor, fog[0], fog[1], fog[2], fog[3]);
        GL20.glUniform1f(fogStart, RenderSystem.getShaderFogStart());
        GL20.glUniform1f(fogEnd, RenderSystem.getShaderFogEnd());
        GL20.glUniform1i(fogShape, RenderSystem.getShaderFogShape().getIndex());
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(lightmap);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(atlas);
        GlStateManager._glBindVertexArray(vao);
        GL43.glBindVertexBuffer(0, Stream.buffer(), offset, STRIDE);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, instances);
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}

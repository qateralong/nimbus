package com.qateralong.nimbus.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class HiZ {
    private static final int SLOTS = 4;
    private static final double MAX_JUMP = 4.0;
    private static boolean failed;
    private static int reduce;
    private static int reduceSource;
    private static int reduceLevel;
    private static int reduceSize;
    private static int cull;
    private static int cullBase;
    private static int cullCount;
    private static int cullSlot;
    private static int cullMvp;
    private static int cullBlock;
    private static int cullFrac;
    private static int cullSize;
    private static int cullLevels;
    private static int pyramid;
    private static int sourceWidth;
    private static int sourceHeight;
    private static int width;
    private static int height;
    private static int levels;
    private static int stats;
    private static long statsAddress;
    private static final Matrix4f PREVIOUS = new Matrix4f();
    private static double prevX;
    private static double prevY;
    private static double prevZ;
    private static int capturedFrame = Integer.MIN_VALUE;
    static int culledDraws;
    static long culledQuads;
    static boolean culling;

    private HiZ() {
    }

    private static boolean init() {
        if (failed) {
            return false;
        }
        if (cull != 0) {
            return true;
        }
        try {
            reduce = program("hiz_reduce.csh");
            reduceSource = GL20.glGetUniformLocation(reduce, "Source");
            reduceLevel = GL20.glGetUniformLocation(reduce, "SourceLevel");
            reduceSize = GL20.glGetUniformLocation(reduce, "SourceSize");
            cull = program("hiz_cull.csh");
            cullBase = GL20.glGetUniformLocation(cull, "CommandBase");
            cullCount = GL20.glGetUniformLocation(cull, "CommandCount");
            cullSlot = GL20.glGetUniformLocation(cull, "StatsSlot");
            cullMvp = GL20.glGetUniformLocation(cull, "PrevMvp");
            cullBlock = GL20.glGetUniformLocation(cull, "PrevBlock");
            cullFrac = GL20.glGetUniformLocation(cull, "PrevFrac");
            cullSize = GL20.glGetUniformLocation(cull, "PyramidSize");
            cullLevels = GL20.glGetUniformLocation(cull, "PyramidLevels");
            GlStateManager._glUseProgram(cull);
            GL20.glUniform1i(GL20.glGetUniformLocation(cull, "Pyramid"), 0);
            GlStateManager._glUseProgram(0);
            stats = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, stats);
            int flags = GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
            GL44.glBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, SLOTS * 8L, flags);
            ByteBuffer mapped = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, SLOTS * 8L, flags);
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            if (mapped == null) {
                throw new IOException("Hi-Z stats buffer could not be mapped");
            }
            statsAddress = MemoryUtil.memAddress(mapped);
            MemoryUtil.memSet(statsAddress, 0, SLOTS * 8L);
            return true;
        } catch (Throwable t) {
            failed = true;
            Nimbus.LOGGER.error("Nimbus occlusion culling failed to start", t);
            return false;
        }
    }

    private static int program(String name) throws IOException {
        int shader = TerrainProgram.compile(GL43.GL_COMPUTE_SHADER, TerrainProgram.source(name));
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, shader);
        GL20.glLinkProgram(id);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
            throw new IOException("Hi-Z program link failed: " + GL20.glGetProgramInfoLog(id));
        }
        return id;
    }

    static void capture(Matrix4f projection, Matrix4f modelView, double x, double y, double z, int frame) {
        if (!init()) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int depth = target.getDepthTextureId();
        if (depth <= 0 || target.width <= 1 || target.height <= 1) {
            capturedFrame = Integer.MIN_VALUE;
            return;
        }
        if (pyramid == 0 || target.width != sourceWidth || target.height != sourceHeight) {
            allocate(target.width, target.height);
        }
        GlStateManager._glUseProgram(reduce);
        GL20.glUniform1i(reduceSource, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int sourceW = sourceWidth;
        int sourceH = sourceHeight;
        for (int level = 0; level < levels; level++) {
            GlStateManager._bindTexture(level == 0 ? depth : pyramid);
            GL20.glUniform1i(reduceLevel, level == 0 ? 0 : level - 1);
            GL20.glUniform2i(reduceSize, sourceW, sourceH);
            int w = Math.max(1, width >> level);
            int h = Math.max(1, height >> level);
            GL42.glBindImageTexture(0, pyramid, level, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);
            GL43.glDispatchCompute((w + 7) >> 3, (h + 7) >> 3, 1);
            GL42.glMemoryBarrier(GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            sourceW = w;
            sourceH = h;
        }
        GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);
        GlStateManager._bindTexture(0);
        restore();
        projection.mul(modelView, PREVIOUS);
        prevX = x;
        prevY = y;
        prevZ = z;
        capturedFrame = frame;
    }

    private static void allocate(int sourceW, int sourceH) {
        if (pyramid != 0) {
            GlStateManager._deleteTexture(pyramid);
        }
        sourceWidth = sourceW;
        sourceHeight = sourceH;
        width = Math.max(1, sourceW >> 1);
        height = Math.max(1, sourceH >> 1);
        levels = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
        pyramid = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(pyramid);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, levels, GL30.GL_R32F, width, height);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        capturedFrame = Integer.MIN_VALUE;
    }

    static void cull(Commands[] lists, double x, double y, double z, int frame) {
        culling = false;
        culledDraws = 0;
        culledQuads = 0L;
        if (cull == 0 || capturedFrame != frame - 1) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target.width != sourceWidth || target.height != sourceHeight
                || Math.abs(x - prevX) > MAX_JUMP || Math.abs(y - prevY) > MAX_JUMP || Math.abs(z - prevZ) > MAX_JUMP) {
            return;
        }
        int slot = frame & (SLOTS - 1);
        long at = statsAddress + slot * 8L;
        culledDraws = MemoryUtil.memGetInt(at);
        culledQuads = MemoryUtil.memGetInt(at + 4) & 0xFFFFFFFFL;
        MemoryUtil.memPutInt(at, 0);
        MemoryUtil.memPutInt(at + 4, 0);
        GlStateManager._glUseProgram(cull);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(cullMvp, false, PREVIOUS.get(stack.mallocFloat(16)));
        }
        int bx = (int) Math.floor(prevX);
        int by = (int) Math.floor(prevY);
        int bz = (int) Math.floor(prevZ);
        GL20.glUniform3i(cullBlock, bx, by, bz);
        GL20.glUniform3f(cullFrac, (float) (prevX - bx), (float) (prevY - by), (float) (prevZ - bz));
        GL20.glUniform2i(cullSize, width, height);
        GL20.glUniform1i(cullLevels, levels);
        GL30.glUniform1ui(cullSlot, slot * 2);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(pyramid);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, Stream.buffer());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, Origins.buffer());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, stats);
        for (Commands list : lists) {
            if (list.count == 0 || list.offset < 0) {
                continue;
            }
            GL30.glUniform1ui(cullBase, (int) (list.offset >> 2));
            GL30.glUniform1ui(cullCount, list.count);
            GL43.glDispatchCompute((list.count + 63) >> 6, 1, 1);
            culling = true;
        }
        GL42.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT | GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, 0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, 0);
        GlStateManager._bindTexture(0);
        restore();
    }

    private static void restore() {
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
    }
}

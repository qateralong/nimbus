package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

// Model parts drawn as instances of a static mesh: every part's cubes are uploaded once in part space, and a drawn part
// only writes its pose, normal matrix, light, overlay and colour. Opaque and cutout entity layers of the entity batch only.
public final class EntityInstances {
    public interface Mesh {
        int nimbus$mesh();

        void nimbus$setMesh(int mesh);
    }

    public interface Cube {
        ModelPart.Polygon[] nimbus$polygons();
    }

    private static final int VERTEX = 24;
    private static final int STRIDE = 96;
    private static final int COMMAND = 20;
    private static final int MAX_QUADS = 16384;
    private static final long MESH_LIMIT = 32L << 20;
    private static final Matrix4f MVP = new Matrix4f();
    private static final Vector3f LIGHT0 = new Vector3f();
    private static final Vector3f LIGHT1 = new Vector3f();
    private static final Reference2ObjectOpenHashMap<RenderType, Batch> BATCHES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<RenderType, Boolean> SUPPORTED = new Reference2ObjectOpenHashMap<>();

    private static int state;
    private static int solidProgram;
    private static int cutoutProgram;
    private static int[] solidUniforms;
    private static int[] cutoutUniforms;
    private static int vao;
    private static int meshBuffer;
    private static int indexBuffer;
    private static ByteBuffer meshes = MemoryUtil.memAlloc(1 << 20);
    private static int meshVertices;
    private static int uploadedVertices;
    private static int[] meshFirst = new int[256];
    private static int[] meshQuads = new int[256];
    private static int meshCount;
    public static int generation = 1;
    public static int drawn;
    private static ByteBuffer sorted = MemoryUtil.memAlloc(STRIDE * 1024);
    private static ByteBuffer commands = MemoryUtil.memAlloc(COMMAND * 256);

    private static final class Batch {
        ByteBuffer data = MemoryUtil.memAlloc(STRIDE * 256);
        long[] keys = new long[256];
        int count;
    }

    private EntityInstances() {
    }

    public static boolean enabled() {
        NimbusConfig config = NimbusConfig.get();
        return config.enabled && config.entityInstancing && available();
    }

    private static boolean available() {
        if (state == 0) {
            state = init() ? 1 : -1;
        }
        return state == 1;
    }

    private static boolean init() {
        if (!Terrain.supported() || !Stream.available()) {
            return false;
        }
        try {
            String vsh = source("entity.vsh");
            String fsh = source("entity.fsh");
            int line = fsh.indexOf('\n') + 1;
            solidProgram = link(vsh, fsh);
            cutoutProgram = link(vsh, fsh.substring(0, line) + "#define ALPHA_CUTOFF\n" + fsh.substring(line));
            solidUniforms = uniforms(solidProgram);
            cutoutUniforms = uniforms(cutoutProgram);
            meshBuffer = GlStateManager._glGenBuffers();
            indexBuffer = GlStateManager._glGenBuffers();
            ByteBuffer indices = MemoryUtil.memAlloc(MAX_QUADS * 6 * 2);
            for (int q = 0; q < MAX_QUADS; q++) {
                int v = q * 4;
                indices.putShort((short) v).putShort((short) (v + 1)).putShort((short) (v + 2));
                indices.putShort((short) (v + 2)).putShort((short) (v + 3)).putShort((short) v);
            }
            indices.flip();
            vao = GL30.glGenVertexArrays();
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(indices);
            attribute(0, 3, GL11.GL_FLOAT, false, 0, 0);
            attribute(1, 2, GL11.GL_FLOAT, false, 12, 0);
            attribute(2, 3, GL11.GL_BYTE, true, 20, 0);
            attribute(3, 4, GL11.GL_FLOAT, false, 0, 1);
            attribute(4, 4, GL11.GL_FLOAT, false, 16, 1);
            attribute(5, 4, GL11.GL_FLOAT, false, 32, 1);
            attribute(6, 3, GL11.GL_FLOAT, false, 48, 1);
            attribute(7, 3, GL11.GL_FLOAT, false, 60, 1);
            attribute(8, 3, GL11.GL_FLOAT, false, 72, 1);
            GL20.glEnableVertexAttribArray(9);
            GL43.glVertexAttribIFormat(9, 2, GL11.GL_SHORT, 84);
            GL43.glVertexAttribBinding(9, 1);
            GL20.glEnableVertexAttribArray(10);
            GL43.glVertexAttribIFormat(10, 2, GL11.GL_SHORT, 88);
            GL43.glVertexAttribBinding(10, 1);
            attribute(11, 4, GL11.GL_UNSIGNED_BYTE, true, 92, 1);
            GL43.glVertexBindingDivisor(1, 1);
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
            GlStateManager._glUseProgram(0);
            ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
            ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
            Nimbus.LOGGER.info("Nimbus instanced entity models ready");
            return true;
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Nimbus instanced entity models unavailable", t);
            return false;
        }
    }

    private static void attribute(int index, int size, int type, boolean normalized, int offset, int binding) {
        GL20.glEnableVertexAttribArray(index);
        GL43.glVertexAttribFormat(index, size, type, normalized, offset);
        GL43.glVertexAttribBinding(index, binding);
    }

    private static String source(String name) throws java.io.IOException {
        try (var stream = EntityInstances.class.getResourceAsStream("/assets/nimbus/shaders/entity/" + name)) {
            if (stream == null) {
                throw new java.io.IOException("Missing shader " + name);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static int link(String vsh, String fsh) throws java.io.IOException {
        int vertex = TerrainProgram.compile(GL20.GL_VERTEX_SHADER, vsh);
        int fragment = TerrainProgram.compile(GL20.GL_FRAGMENT_SHADER, fsh);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new java.io.IOException("Entity program link failed: " + GL20.glGetProgramInfoLog(program));
        }
        GlStateManager._glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler1"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler2"), 2);
        return program;
    }

    private static int[] uniforms(int program) {
        String[] names = {"MvpMat", "FogShape", "Light0_Direction", "Light1_Direction", "ColorModulator", "FogStart", "FogEnd", "FogColor"};
        int[] out = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = GL20.glGetUniformLocation(program, names[i]);
        }
        return out;
    }

    // vanilla entity shaders this program reproduces; z-offset, translucent and emissive layers keep their own path
    private static boolean supported(RenderType type) {
        Boolean known = SUPPORTED.get(type);
        if (known != null) {
            return known;
        }
        boolean result = false;
        if (type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS && !type.sortOnUpload()) {
            String text = type.toString();
            int open = text.indexOf('[');
            int close = text.indexOf(':');
            String name = open >= 0 && close > open ? text.substring(open + 1, close) : "";
            result = name.equals("entity_solid") || name.equals("entity_cutout") || name.equals("entity_cutout_no_cull") || name.equals("entity_smooth_cutout");
        }
        if (SUPPORTED.size() > 4096) {
            SUPPORTED.clear();
        }
        SUPPORTED.put(type, result);
        return result;
    }

    private static boolean cutout(RenderType type) {
        String text = type.toString();
        return !text.contains("[entity_solid:");
    }

    // false: the caller writes vertices as usual
    public static boolean capture(Object consumer, ModelPart part, List<ModelPart.Cube> cubes, PoseStack.Pose pose, int light, int overlay, int color) {
        RenderType type = EntityBatch.typeOf(consumer);
        if (type == null || !NimbusConfig.get().entityInstancing || part.getClass() != ModelPart.class || !supported(type) || !available()) {
            return false;
        }
        int mesh = mesh((Mesh) (Object) part, cubes);
        if (mesh < 0) {
            return false;
        }
        Batch batch = BATCHES.get(type);
        if (batch == null) {
            batch = new Batch();
            BATCHES.put(type, batch);
        }
        int index = batch.count;
        if (batch.data.capacity() < (index + 1) * STRIDE) {
            batch.data = MemoryUtil.memRealloc(batch.data, batch.data.capacity() * 2);
        }
        if (batch.keys.length <= index) {
            batch.keys = Arrays.copyOf(batch.keys, batch.keys.length * 2);
        }
        long p = MemoryUtil.memAddress(batch.data) + (long) index * STRIDE;
        Matrix4f m = pose.pose();
        put4(p, m.m00(), m.m10(), m.m20(), m.m30());
        put4(p + 16L, m.m01(), m.m11(), m.m21(), m.m31());
        put4(p + 32L, m.m02(), m.m12(), m.m22(), m.m32());
        Matrix3f n = pose.normal();
        put3(p + 48L, n.m00(), n.m10(), n.m20());
        put3(p + 60L, n.m01(), n.m11(), n.m21());
        put3(p + 72L, n.m02(), n.m12(), n.m22());
        MemoryUtil.memPutInt(p + 84L, light);
        MemoryUtil.memPutInt(p + 88L, overlay);
        MemoryUtil.memPutInt(p + 92L, net.minecraft.util.FastColor.ABGR32.fromArgb32(color));
        batch.keys[index] = (long) mesh << 32 | index;
        batch.count = index + 1;
        return true;
    }

    private static void put4(long p, float a, float b, float c, float d) {
        MemoryUtil.memPutFloat(p, a);
        MemoryUtil.memPutFloat(p + 4L, b);
        MemoryUtil.memPutFloat(p + 8L, c);
        MemoryUtil.memPutFloat(p + 12L, d);
    }

    private static void put3(long p, float a, float b, float c) {
        MemoryUtil.memPutFloat(p, a);
        MemoryUtil.memPutFloat(p + 4L, b);
        MemoryUtil.memPutFloat(p + 8L, c);
    }

    private static int mesh(Mesh holder, List<ModelPart.Cube> cubes) {
        int cached = holder.nimbus$mesh();
        if (cached != 0 && (cached >>> 20) == (generation & 0xFFF)) {
            int id = (cached & 0xFFFFF) - 1;
            return id;
        }
        int id = build(cubes);
        holder.nimbus$setMesh((generation & 0xFFF) << 20 | (id + 1) & 0xFFFFF);
        return id;
    }

    private static int build(List<ModelPart.Cube> cubes) {
        int quads = 0;
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon polygon : ((Cube) (Object) cube).nimbus$polygons()) {
                if (polygon.vertices.length != 4) {
                    return -1;
                }
                quads++;
            }
        }
        if (quads == 0 || quads > MAX_QUADS) {
            return -1;
        }
        long bytes = (long) (meshVertices + quads * 4) * VERTEX;
        if (bytes > MESH_LIMIT || meshCount >= 0xFFFFE) {
            reset();
            bytes = (long) quads * 4 * VERTEX;
        }
        if (meshes.capacity() < bytes) {
            meshes = MemoryUtil.memRealloc(meshes, (int) Math.max(bytes, meshes.capacity() * 2L));
        }
        long p = MemoryUtil.memAddress(meshes) + (long) meshVertices * VERTEX;
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon polygon : ((Cube) (Object) cube).nimbus$polygons()) {
                byte nx = normal(polygon.normal.x());
                byte ny = normal(polygon.normal.y());
                byte nz = normal(polygon.normal.z());
                for (ModelPart.Vertex vertex : polygon.vertices) {
                    MemoryUtil.memPutFloat(p, vertex.pos.x() / 16.0F);
                    MemoryUtil.memPutFloat(p + 4L, vertex.pos.y() / 16.0F);
                    MemoryUtil.memPutFloat(p + 8L, vertex.pos.z() / 16.0F);
                    MemoryUtil.memPutFloat(p + 12L, vertex.u);
                    MemoryUtil.memPutFloat(p + 16L, vertex.v);
                    MemoryUtil.memPutByte(p + 20L, nx);
                    MemoryUtil.memPutByte(p + 21L, ny);
                    MemoryUtil.memPutByte(p + 22L, nz);
                    MemoryUtil.memPutByte(p + 23L, (byte) 0);
                    p += VERTEX;
                }
            }
        }
        if (meshCount == meshFirst.length) {
            meshFirst = Arrays.copyOf(meshFirst, meshCount * 2);
            meshQuads = Arrays.copyOf(meshQuads, meshCount * 2);
        }
        meshFirst[meshCount] = meshVertices;
        meshQuads[meshCount] = quads;
        meshVertices += quads * 4;
        return meshCount++;
    }

    private static byte normal(float value) {
        return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
    }

    // drops every mesh: parts notice the new generation and build theirs again
    private static void reset() {
        for (Batch batch : BATCHES.values()) {
            batch.count = 0;
        }
        meshVertices = 0;
        uploadedVertices = -1;
        meshCount = 0;
        generation++;
    }

    public static void draw(RenderType type) {
        Batch batch = BATCHES.get(type);
        if (batch == null || batch.count == 0) {
            return;
        }
        int count = batch.count;
        batch.count = 0;
        drawn += count;
        if (uploadedVertices != meshVertices) {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, meshBuffer);
            meshes.limit(meshVertices * VERTEX).position(0);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshes, GL15.GL_STATIC_DRAW);
            meshes.clear();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            uploadedVertices = meshVertices;
        }
        long[] keys = batch.keys;
        Arrays.sort(keys, 0, count);
        if (sorted.capacity() < count * STRIDE) {
            sorted = MemoryUtil.memRealloc(sorted, count * STRIDE * 2);
        }
        if (commands.capacity() < count * COMMAND) {
            commands = MemoryUtil.memRealloc(commands, count * COMMAND * 2);
        }
        long source = MemoryUtil.memAddress(batch.data);
        long target = MemoryUtil.memAddress(sorted);
        long command = MemoryUtil.memAddress(commands);
        int draws = 0;
        int run = 0;
        for (int i = 0; i < count; i++) {
            int mesh = (int) (keys[i] >>> 32);
            int index = (int) keys[i];
            MemoryUtil.memCopy(source + (long) index * STRIDE, target + (long) i * STRIDE, STRIDE);
            if (i + 1 == count || (int) (keys[i + 1] >>> 32) != mesh) {
                long c = command + (long) draws * COMMAND;
                MemoryUtil.memPutInt(c, meshQuads[mesh] * 6);
                MemoryUtil.memPutInt(c + 4L, i + 1 - run);
                MemoryUtil.memPutInt(c + 8L, 0);
                MemoryUtil.memPutInt(c + 12L, meshFirst[mesh]);
                MemoryUtil.memPutInt(c + 16L, run);
                draws++;
                run = i + 1;
            }
        }
        sorted.limit(count * STRIDE).position(0);
        long instances = Stream.write(sorted, STRIDE);
        sorted.clear();
        commands.limit(draws * COMMAND).position(0);
        long commandOffset = Stream.write(commands, COMMAND);
        commands.clear();
        if (instances < 0 || commandOffset < 0) {
            return;
        }
        type.setupRenderState();
        boolean cut = cutout(type);
        int program = cut ? cutoutProgram : solidProgram;
        int[] u = cut ? cutoutUniforms : solidUniforms;
        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(u[0], false, RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), MVP).get(stack.mallocFloat(16)));
        }
        GL20.glUniform1i(u[1], RenderSystem.getShaderFogShape().getIndex());
        Vector3f[] lights = com.qateralong.nimbus.mixin.RenderSystemLightAccessor.nimbus$lights();
        LIGHT0.set(lights[0]);
        LIGHT1.set(lights[1]);
        GL20.glUniform3f(u[2], LIGHT0.x, LIGHT0.y, LIGHT0.z);
        GL20.glUniform3f(u[3], LIGHT1.x, LIGHT1.y, LIGHT1.z);
        float[] color = RenderSystem.getShaderColor();
        GL20.glUniform4f(u[4], color[0], color[1], color[2], color[3]);
        GL20.glUniform1f(u[5], RenderSystem.getShaderFogStart());
        GL20.glUniform1f(u[6], RenderSystem.getShaderFogEnd());
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(u[7], fog[0], fog[1], fog[2], fog[3]);
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(2));
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(1));
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(0));
        GlStateManager._glBindVertexArray(vao);
        GL43.glBindVertexBuffer(0, meshBuffer, 0, VERTEX);
        GL43.glBindVertexBuffer(1, Stream.buffer(), instances, STRIDE);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, Stream.buffer());
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_SHORT, commandOffset, draws, COMMAND);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        type.clearRenderState();
    }

    public static String debug() {
        return "entity instancing " + (state == 1 ? "on" : state == 0 ? "unused" : "unavailable") + ", " + meshCount + " part meshes, " + meshVertices + " vertices, "
                + drawn + " parts drawn since start";
    }

    public static void discard() {
        for (Batch batch : BATCHES.values()) {
            batch.count = 0;
        }
    }
}

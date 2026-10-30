package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.Compat;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.LevelRendererAccessor;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class Terrain {
    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    private static volatile boolean ready;
    private static boolean failed;
    private static TerrainProgram program;
    private static TerrainProgram cutoutProgram;
    private static final Matrix4f MVP = new Matrix4f();
    private static float fogLimit = Float.MAX_VALUE;
    private static boolean fogCylinder;
    public static int fogCulled;
    private static int markedTick = -1;
    private static final ObjectArrayList<int[]> SPRITES_SEEN = new ObjectArrayList<>();
    private static int lastStamp = -1;
    private static int lastVersion = -1;
    private static double lastX = Double.NaN;
    private static double lastY = Double.NaN;
    private static double lastZ = Double.NaN;
    private static float lastFog = Float.NaN;
    private static boolean lastCylinder;
    private static int reused;
    private static final boolean BENCH = System.getProperty("nimbus.bench") != null;
    private static long benchNanos;
    private static long benchSections;
    private static long benchCulled;
    private static int benchFrames;
    private static Arena arena;
    private static final int MAX_QUADS_PER_DRAW = 1 << 14;
    private static Commands[] LISTS = new Commands[0];
    private static int builtFrame = -1;
    private static int vao;
    private static int indices;
    private static int frame;
    public static int lastDraws;

    private Terrain() {
    }

    public static boolean supported() {
        return !SODIUM;
    }

    public static boolean active() {
        return ready && NimbusConfig.get().enabled && NimbusConfig.get().terrainRenderer;
    }

    public static void init() {
        if (SODIUM || failed || program != null) {
            ready = program != null;
            return;
        }
        if (!NimbusConfig.get().terrainRenderer) {
            return;
        }
        try {
            if (!GL.getCapabilities().OpenGL46 || !Stream.available()) {
                Nimbus.LOGGER.info("Nimbus terrain renderer needs OpenGL 4.6, using vanilla");
                failed = true;
                return;
            }
            program = new TerrainProgram(false);
            cutoutProgram = new TerrainProgram(true);
            arena = new Arena(1 << 19);
            vao = GlStateManager._glGenVertexArrays();
            GlStateManager._glBindVertexArray(vao);
            GL20.glEnableVertexAttribArray(0);
            GL43.glVertexAttribIFormat(0, 3, GL11.GL_INT, 0);
            GL43.glVertexAttribBinding(0, 0);
            GL43.glVertexBindingDivisor(0, 1);
            indices = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices);
            java.nio.ShortBuffer pattern = MemoryUtil.memAllocShort(MAX_QUADS_PER_DRAW * 6);
            for (int quad = 0; quad < MAX_QUADS_PER_DRAW; quad++) {
                short base = (short) (quad * 4);
                pattern.put(base).put((short) (base + 1)).put((short) (base + 2)).put((short) (base + 2)).put((short) (base + 3)).put(base);
            }
            pattern.flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, pattern, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(pattern);
            GlStateManager._glBindVertexArray(0);
            ready = true;
            Nimbus.LOGGER.info("Nimbus terrain renderer ready");
        } catch (Throwable t) {
            failed = true;
            Nimbus.LOGGER.error("Nimbus terrain renderer failed to start, using vanilla", t);
        }
    }

    public static void toggled() {
        init();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    public static void endFrame() {
        frame++;
        AnimatedSprites.enabled = active();
    }

    public static String debug() {
        if (!active()) {
            return null;
        }
        return String.format("Nimbus terrain: %d MB of %d MB, %d draws, %d visible, %d fogged, %d occluded", (long) arena.usedQuads() * Arena.QUAD_BYTES >> 20,
                (long) arena.capacityQuads() * Arena.QUAD_BYTES >> 20, lastDraws, Visibility.count, fogCulled, HiZ.culledDraws);
    }

    public static boolean meshing() {
        return active() && NimbusConfig.get().nimbusMesher;
    }

    public static CompletableFuture<Void> uploadLayer(MeshData mesh, VertexBuffer buffer, Queue<Runnable> queue) {
        Packed packed = ((PackedMesh) mesh).nimbus$packed();
        if (packed != null) {
            return uploadPacked(mesh, packed, buffer, queue);
        }
        if (!active() || !Packer.supported(mesh)) {
            return null;
        }
        int quads = mesh.drawState().vertexCount() / 4;
        long original;
        int[] order;
        Faces faces = null;
        int[] sprites;
        try {
            sprites = AnimatedSprites.find(mesh, quads);
            original = Packer.pack(mesh);
            order = Packer.order(mesh.indexBuffer(), mesh.drawState().indexType(), quads);
            if (order == null && quads > 0) {
                long[] grouped = new long[1];
                faces = Packer.group(mesh, original, quads, grouped);
                MemoryUtil.nmemFree(original);
                original = grouped[0];
            }
        } finally {
            mesh.close();
        }
        TerrainSlot slot = (TerrainSlot) buffer;
        long data = original;
        Faces groups = faces;
        int[] animated = sprites;
        return CompletableFuture.runAsync(() -> store(slot, data, quads, order, groups, animated), queue::add);
    }

    private static CompletableFuture<Void> uploadPacked(MeshData mesh, Packed packed, VertexBuffer buffer, Queue<Runnable> queue) {
        int quads = mesh.drawState().vertexCount() / 4;
        if (!active()) {
            mesh.close();
            return CompletableFuture.completedFuture(null);
        }
        long data;
        try {
            data = Arena.allocateCpu(Math.max(1, quads));
            MemoryUtil.memCopy(MemoryUtil.memAddress(mesh.vertexBuffer()), data, (long) quads * Arena.QUAD_BYTES);
        } finally {
            mesh.close();
        }
        TerrainSlot slot = (TerrainSlot) buffer;
        int[] order = packed.order;
        if (order == null && packed.centroids != null) {
            order = new int[quads];
            for (int i = 0; i < quads; i++) {
                order[i] = i;
            }
        }
        int[] sorted = order;
        return CompletableFuture.runAsync(() -> store(slot, data, quads, sorted, packed.faces, packed.sprites), queue::add);
    }

    private static void store(TerrainSlot slot, long original, int quads, int[] order, Faces faces, int[] sprites) {
        if (((VertexBuffer) slot).isInvalid() || arena == null) {
            MemoryUtil.nmemFree(original);
            return;
        }
        release(slot);
        long data = Packer.arrange(original, quads, order);
        int start = arena.allocate(quads);
        arena.upload(start, data, quads);
        slot.nimbus$set(start, quads);
        slot.nimbus$setFaces(faces);
        SectionData.set(slot.nimbus$section(), slot.nimbus$layer(), start, quads, faces, sprites);
        if (data != original) {
            MemoryUtil.nmemFree(data);
        }
        if (order != null) {
            slot.nimbus$setCopy(original);
        } else {
            MemoryUtil.nmemFree(original);
        }
    }

    public static CompletableFuture<Void> uploadIndices(ByteBufferBuilder.Result result, VertexBuffer buffer, Queue<Runnable> queue) {
        if (!active()) {
            return null;
        }
        long copy;
        int size;
        try {
            ByteBuffer indices = result.byteBuffer();
            size = indices.remaining();
            copy = MemoryUtil.nmemAlloc(Math.max(1, size));
            MemoryUtil.memCopy(MemoryUtil.memAddress(indices), copy, size);
        } finally {
            result.close();
        }
        TerrainSlot slot = (TerrainSlot) buffer;
        return CompletableFuture.runAsync(() -> {
            try {
                resort(slot, copy, size);
            } finally {
                MemoryUtil.nmemFree(copy);
            }
        }, queue::add);
    }

    private static void resort(TerrainSlot slot, long indices, int size) {
        long original = slot.nimbus$copy();
        int quads = slot.nimbus$count();
        if (original == 0L || quads == 0 || arena == null) {
            return;
        }
        int[] order = Packer.order(MemoryUtil.memByteBuffer(indices, size), VertexFormat.IndexType.least(quads * 4), quads);
        long data = Packer.arrange(original, quads, order);
        arena.upload(slot.nimbus$start(), data, quads);
        if (data != original) {
            MemoryUtil.nmemFree(data);
        }
    }

    public static void release(TerrainSlot slot) {
        if (slot.nimbus$count() > 0 && arena != null) {
            arena.release(slot.nimbus$start(), slot.nimbus$count());
        }
        slot.nimbus$set(0, 0);
        slot.nimbus$setFaces(null);
        SectionData.clear(slot.nimbus$section(), slot.nimbus$layer());
        long copy = slot.nimbus$copy();
        if (copy != 0L) {
            MemoryUtil.nmemFree(copy);
            slot.nimbus$setCopy(0L);
        }
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> DEFERRED = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void compiled(SectionStamp section, SectionRenderDispatcher.CompiledSection compiled) {
        if (SODIUM || arena == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            DEFERRED.add(() -> compiled(section, ((SectionRenderDispatcher.RenderSection) section).getCompiled()));
            return;
        }
        TerrainSlot[] slots = section.nimbus$slots();
        java.util.List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int i = 0; i < slots.length; i++) {
            TerrainSlot slot = slots[i];
            if (slot != null && slot.nimbus$count() > 0 && compiled.isEmpty(layers.get(i))) {
                release(slot);
            }
        }
    }

    private static int layerIndex(RenderType layer) {
        java.util.List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i) == layer) {
                return i;
            }
        }
        return -1;
    }

    private static float cutoff(RenderType layer) {
        if (layer == RenderType.cutoutMipped()) {
            return 0.5F;
        }
        if (layer == RenderType.cutout() || layer == RenderType.tripwire()) {
            return 0.1F;
        }
        return 0.0F;
    }

    private static void resortTranslucent(LevelRendererAccessor renderer, ObjectArrayList<SectionRenderDispatcher.RenderSection> visible,
                                          RenderType layer, double x, double y, double z) {
        double dx = x - renderer.nimbus$xTransparentOld();
        double dy = y - renderer.nimbus$yTransparentOld();
        double dz = z - renderer.nimbus$zTransparentOld();
        if (dx * dx + dy * dy + dz * dz <= 1.0) {
            return;
        }
        int sx = SectionPos.posToSectionCoord(x);
        int sy = SectionPos.posToSectionCoord(y);
        int sz = SectionPos.posToSectionCoord(z);
        boolean moved = sx != SectionPos.posToSectionCoord(renderer.nimbus$xTransparentOld()) || sz != SectionPos.posToSectionCoord(renderer.nimbus$zTransparentOld())
                || sy != SectionPos.posToSectionCoord(renderer.nimbus$yTransparentOld());
        renderer.nimbus$setXTransparentOld(x);
        renderer.nimbus$setYTransparentOld(y);
        renderer.nimbus$setZTransparentOld(z);
        SectionRenderDispatcher dispatcher = renderer.nimbus$sectionRenderDispatcher();
        int resorted = 0;
        for (SectionRenderDispatcher.RenderSection section : visible) {
            if (resorted < 15 && (moved || section.isAxisAlignedWith(sx, sy, sz)) && section.resortTransparency(layer, dispatcher)) {
                resorted++;
            }
        }
    }

    public static void prepare(LevelRenderer levelRenderer, double x, double y, double z) {
        if (!active()) {
            return;
        }
        for (Runnable task; (task = DEFERRED.poll()) != null; ) {
            task.run();
        }
        Origins.flush();
        if (builtFrame != frame) {
            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = ((LevelRendererAccessor) levelRenderer).nimbus$visibleSections();
            long started = BENCH ? System.nanoTime() : 0L;
            fogSetup();
            if (Visibility.valid && LISTS.length > 0 && Visibility.changes == lastStamp && SectionData.version == lastVersion && x == lastX && y == lastY
                    && z == lastZ && fogLimit == lastFog && fogCylinder == lastCylinder) {
                reuse();
                reused++;
            } else {
                build(visible, x, y, z);
                lastStamp = Visibility.valid ? Visibility.changes : -1;
                lastVersion = SectionData.version;
                lastX = x;
                lastY = y;
                lastZ = z;
                lastFog = fogLimit;
                lastCylinder = fogCylinder;
            }
            builtFrame = frame;
            if (NimbusConfig.get().gpuOcclusion) {
                HiZ.cull(LISTS, x, y, z, frame);
            }
            if (BENCH) {
                benchCulled += HiZ.culledQuads;
                benchNanos += System.nanoTime() - started;
                benchSections += Visibility.valid ? Visibility.count : visible.size();
                benchFrames++;
                if (benchFrames >= 600) {
                    int draws = 0;
                    for (Commands list : LISTS) {
                        draws += list.count;
                    }
                    Nimbus.LOGGER.info(String.format(java.util.Locale.ROOT, "Nimbus build: %.3f ms, %d sections, %d draws per frame, %d%% reused, %d quads occluded",
                            benchNanos / 1e6 / benchFrames, benchSections / benchFrames, draws, reused * 100 / benchFrames, benchCulled / benchFrames));
                    reused = 0;
                    benchCulled = 0L;
                    benchNanos = 0L;
                    benchSections = 0L;
                    benchFrames = 0;
                }
            }
        }
    }

    public static boolean render(LevelRenderer levelRenderer, RenderType layer, double x, double y, double z, Matrix4f modelView, Matrix4f projection) {
        if (!active()) {
            return false;
        }
        prepare(levelRenderer, x, y, z);
        LevelRendererAccessor renderer = (LevelRendererAccessor) levelRenderer;
        ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = renderer.nimbus$visibleSections();
        layer.setupRenderState();
        boolean translucent = layer == RenderType.translucent();
        if (translucent) {
            resortTranslucent(renderer, visible, layer, x, y, z);
        }
        int index = layerIndex(layer);
        int draws = 0;
        if (index >= 0 && index < LISTS.length) {
            Commands list = LISTS[index];
            draws = list.count;
            GpuTimer.quads(index, list.quads);
            if (draws > 0 && list.offset >= 0) {
                draw(layer, modelView, projection, list.offset, draws, x, y, z);
            }
        }
        if (layer == RenderType.solid()) {
            lastDraws = 0;
        }
        if (layer == RenderType.cutout() && NimbusConfig.get().gpuOcclusion) {
            HiZ.capture(projection, modelView, x, y, z, frame);
        }
        lastDraws += draws;
        layer.clearRenderState();
        return true;
    }

    private static void reuse() {
        if (markedTick != Nimbus.ticks) {
            markedTick = Nimbus.ticks;
            for (int i = 0, size = SPRITES_SEEN.size(); i < size; i++) {
                AnimatedSprites.mark(SPRITES_SEEN.get(i));
            }
        }
        for (Commands list : LISTS) {
            list.upload();
        }
    }

    private static void fogSetup() {
        float end = RenderSystem.getShaderFogEnd();
        float start = RenderSystem.getShaderFogStart();
        float[] color = RenderSystem.getShaderFogColor();
        if (Compat.shadersOn() || color[3] < 1.0F || start >= end || end <= 0.0F || end > 1.0E6F) {
            fogLimit = Float.MAX_VALUE;
            return;
        }
        fogLimit = end + 0.5F;
        fogCylinder = RenderSystem.getShaderFogShape() == com.mojang.blaze3d.shaders.FogShape.CYLINDER;
    }

    private static void build(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible, double x, double y, double z) {
        int layers = RenderType.chunkBufferLayers().size();
        if (LISTS.length != layers) {
            LISTS = new Commands[layers];
            for (int l = 0; l < layers; l++) {
                LISTS[l] = new Commands();
            }
        }
        int translucentIndex = layerIndex(RenderType.translucent());
        for (int l = 0; l < layers; l++) {
            LISTS[l].clear();
        }
        int[] records = SectionData.records;
        int[][] animatedSprites = SectionData.animated;
        boolean tracked = Visibility.valid;
        int[] visibleIds = Visibility.ids;
        float limit = fogLimit;
        boolean cylinder = fogCylinder;
        int fogged = 0;
        int[] headers = Origins.values;
        boolean markSprites = markedTick != Nimbus.ticks;
        markedTick = Nimbus.ticks;
        SPRITES_SEEN.clear();
        for (int i = 0, size = tracked ? Visibility.count : visible.size(); i < size; i++) {
            int id = tracked ? visibleIds[i] : ((SectionStamp) visible.get(i)).nimbus$id();
            int head = id * 4;
            if (head + 3 >= headers.length) {
                continue;
            }
            int bits = headers[head + 3];
            if (bits == 0) {
                continue;
            }
            float cx = (float) (x - headers[head]);
            float cy = (float) (y - headers[head + 1]);
            float cz = (float) (z - headers[head + 2]);
            if (limit != Float.MAX_VALUE) {
                float dx = Math.max(0.0F, Math.max(-cx, cx - 16.0F));
                float dy = Math.max(0.0F, Math.max(-cy, cy - 16.0F));
                float dz = Math.max(0.0F, Math.max(-cz, cz - 16.0F));
                if (cylinder ? dx * dx + dz * dz > limit * limit || dy > limit : dx * dx + dy * dy + dz * dz > limit * limit) {
                    fogged++;
                    continue;
                }
            }
            for (int l = 0; l < layers && bits != 0; l++, bits >>>= 1) {
                if ((bits & 1) == 0) {
                    continue;
                }
                int key = id * SectionData.LAYERS + l;
                int at = key * SectionData.STRIDE;
                int flags = records[at + SectionData.FLAGS];
                if ((flags & SectionData.ANIMATED) != 0) {
                    int[] sprites = animatedSprites[key];
                    if (sprites != null) {
                        SPRITES_SEEN.add(sprites);
                        if (markSprites) {
                            AnimatedSprites.mark(sprites);
                        }
                    }
                }
                int base = records[at + SectionData.START];
                Commands list = LISTS[l];
                if (l == translucentIndex || (flags & SectionData.GROUPED) == 0) {
                    list.add(base, records[at + SectionData.COUNT], id, MAX_QUADS_PER_DRAW);
                    continue;
                }
                int o = at + SectionData.OFFSETS;
                int p = at + SectionData.PLANES;
                int shown = (cy < Float.intBitsToFloat(records[p]) + 0.01F ? 1 : 0)
                        | (cy > Float.intBitsToFloat(records[p + 1]) - 0.01F ? 2 : 0)
                        | (cz < Float.intBitsToFloat(records[p + 2]) + 0.01F ? 4 : 0)
                        | (cz > Float.intBitsToFloat(records[p + 3]) - 0.01F ? 8 : 0)
                        | (cx < Float.intBitsToFloat(records[p + 4]) + 0.01F ? 16 : 0)
                        | (cx > Float.intBitsToFloat(records[p + 5]) - 0.01F ? 32 : 0)
                        | 64;
                int present = flags >>> SectionData.GROUP_SHIFT & 0x7F;
                int runStart = -1;
                while (present != 0) {
                    int group = Integer.numberOfTrailingZeros(present);
                    present &= present - 1;
                    if ((shown >>> group & 1) != 0) {
                        if (runStart < 0) {
                            runStart = records[o + group];
                        }
                    } else if (runStart >= 0) {
                        list.add(base + runStart, records[o + group] - runStart, id, MAX_QUADS_PER_DRAW);
                        runStart = -1;
                    }
                }
                if (runStart >= 0) {
                    list.add(base + runStart, records[o + Faces.GROUPS] - runStart, id, MAX_QUADS_PER_DRAW);
                }
            }
        }
        fogCulled = fogged;
        if (translucentIndex >= 0) {
            LISTS[translucentIndex].reverse();
        }
        for (int l = 0; l < layers; l++) {
            LISTS[l].upload();
        }
    }

    private static void draw(RenderType layer, Matrix4f modelView, Matrix4f projection, long commandOffset, int draws, double x, double y, double z) {
        int atlas = RenderSystem.getShaderTexture(0);
        int lightmap = RenderSystem.getShaderTexture(2);
        float alpha = cutoff(layer);
        TerrainProgram program = alpha > 0.0F ? cutoutProgram : Terrain.program;
        GlStateManager._glUseProgram(program.id);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(program.mvp, false, projection.mul(modelView, MVP).get(stack.mallocFloat(16)));
        }
        float[] color = RenderSystem.getShaderColor();
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.colorModulator, color[0], color[1], color[2], color[3]);
        GL20.glUniform4f(program.fogColor, fog[0], fog[1], fog[2], fog[3]);
        GL20.glUniform1f(program.fogStart, RenderSystem.getShaderFogStart());
        GL20.glUniform1f(program.fogEnd, RenderSystem.getShaderFogEnd());
        GL20.glUniform1i(program.fogShape, RenderSystem.getShaderFogShape().getIndex());
        GL20.glUniform1f(program.alphaCutoff, alpha);
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        GL20.glUniform3i(program.cameraBlock, bx, by, bz);
        GL20.glUniform3f(program.cameraFrac, (float) (x - bx), (float) (y - by), (float) (z - bz));
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(lightmap);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(atlas);
        GlStateManager._glBindVertexArray(vao);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, arena.buffer());
        GL43.glBindVertexBuffer(0, Origins.buffer(), 0, 16);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, Stream.buffer());
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_SHORT, commandOffset, draws, 20);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}

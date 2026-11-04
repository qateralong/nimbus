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
import java.util.List;
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
    private static volatile boolean live;
    private static boolean failed;
    private static TerrainProgram program;
    private static TerrainProgram cutoutProgram;
    private static final Matrix4f MVP = new Matrix4f();
    private static float fogLimit = Float.MAX_VALUE;
    private static boolean fogCylinder;
    public static int fogCulled;
    private static int markedTick = -1;
    private static long[] spritesSeen = new long[16];
    private static int[] setStamps = new int[64];
    private static int buildStamp;

    private static long[] addSprites(long[] seen, int[] sprites) {
        for (int sprite : sprites) {
            if (sprite >> 6 >= seen.length) {
                seen = spritesSeen = java.util.Arrays.copyOf(seen, Math.max(seen.length * 2, (sprite >> 6) + 1));
            }
            seen[sprite >> 6] |= 1L << sprite;
        }
        return seen;
    }
    private static int lastStamp = -1;
    private static int lastVersion = -1;
    private static double lastX = Double.NaN;
    private static double lastY = Double.NaN;
    private static double lastZ = Double.NaN;
    private static float lastFog = Float.NaN;
    private static boolean lastCylinder;
    private static int reused;
    private static final boolean BENCH = (System.getProperty("nimbus.bench") != null && !"prod".equals(System.getProperty("nimbus.bench")));
    private static long benchNanos;
    private static long benchSections;
    private static long benchCulled;
    private static int benchFrames;
    private static Arena arena;
    private static boolean full;
    private static final int MAX_QUADS_PER_DRAW = 1 << 14;
    private static Commands[] LISTS = new Commands[0];
    private static int builtFrame = -1;
    private static int vao;
    private static int indices;
    private static int frame;
    public static int lastDraws;

    private Terrain() {
    }

    static Commands[] lists() {
        return LISTS;
    }

    static float fogLimit() {
        return fogLimit;
    }

    public static boolean supported() {
        return !SODIUM;
    }

    // Latched: section buffers built for one renderer are unusable by the other, so the switch only happens on a full reload
    public static boolean active() {
        return live;
    }

    public static void sync() {
        live = ready && NimbusConfig.get().enabled && NimbusConfig.get().terrainRenderer;
    }

    public static void init() {
        if (SODIUM || failed || program != null) {
            ready = program != null;
            sync();
            return;
        }
        if (!NimbusConfig.get().terrainRenderer) {
            return;
        }
        try {
            // NVIDIA reports the requested 3.2 context version and exposes the rest as extensions, so check features
            org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
            String gpu = GL11.glGetString(GL11.GL_RENDERER) + ", OpenGL " + GL11.glGetString(GL11.GL_VERSION);
            boolean features = caps.OpenGL43 || (caps.GL_ARB_shader_storage_buffer_object && caps.GL_ARB_multi_draw_indirect
                    && caps.GL_ARB_vertex_attrib_binding && caps.GL_ARB_base_instance);
            if (!features || !(caps.OpenGL44 || caps.GL_ARB_buffer_storage)) {
                Nimbus.LOGGER.info("Nimbus terrain renderer needs OpenGL 4.3 features and buffer storage ({}), using vanilla", gpu);
                failed = true;
                notifyFallback("nimbus.engine.no_gl43");
                return;
            }
            if (!Stream.available()) {
                Nimbus.LOGGER.info("Nimbus terrain renderer: no persistent stream buffer ({}), using vanilla", gpu);
                failed = true;
                notifyFallback("nimbus.engine.failed");
                return;
            }
            Nimbus.LOGGER.info("Nimbus terrain renderer on {}", gpu);
            Arena.maxPages = Math.max(1, Math.min(Arena.LIMIT, Math.min(GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS),
                    GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS))));
            program = new TerrainProgram(false);
            cutoutProgram = new TerrainProgram(true);
            arena = new Arena();
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
            sync();
            Nimbus.LOGGER.info("Nimbus terrain renderer ready");
        } catch (Throwable t) {
            failed = true;
            Nimbus.LOGGER.error("Nimbus terrain renderer failed to start, using vanilla", t);
            notifyFallback("nimbus.engine.failed");
        }
    }

    private static void notifyFallback(String key) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(minecraft.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                net.minecraft.network.chat.Component.translatable("nimbus.engine.fallback"), net.minecraft.network.chat.Component.translatable(key)));
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
        String text = String.format("Nimbus terrain: %d MB of %d MB, %d draws, %d visible, %d fogged, %d occluded", (long) arena.usedQuads() * Arena.QUAD_BYTES >> 20,
                (long) arena.capacityQuads() * Arena.QUAD_BYTES >> 20, lastDraws, Visibility.count, fogCulled, HiZ.culledDraws);
        return Traversal.enabled() ? text + ", " + Traversal.debug() : text;
    }

    // GPU per-draw frustum test; Hi-Z occlusion writes the same instance counts, so the two never run together
    public static boolean gpuFrustum() {
        return active() && NimbusConfig.get().gpuFrustum && !NimbusConfig.get().gpuOcclusion && GpuFrustum.available();
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
        boolean translucent = slot.nimbus$layer() == layerIndex(RenderType.translucent());
        // translucent quads all facing one axis have a back-to-front order that holds for every camera position,
        // so such a layer keeps its face groups and is never resorted
        boolean flatLayer = false;
        if (translucent) {
            int[] planar = faces == null ? null : planarOrder(original, quads, faces);
            if (planar != null) {
                order = planar;
                flatLayer = true;
            } else if (flat(original, quads)) {
                order = null;
                flatLayer = true;
            } else {
                faces = null;
            }
        }
        long data = Packer.arrange(original, quads, order);
        int start = arena.allocate(quads);
        if (start < 0) {
            if (!full) {
                full = true;
                Nimbus.LOGGER.warn("Nimbus terrain storage is full ({} MB), some sections are skipped", (long) arena.capacityQuads() * Arena.QUAD_BYTES >> 20);
            }
            if (data != original) {
                MemoryUtil.nmemFree(data);
            }
            MemoryUtil.nmemFree(original);
            return;
        }
        arena.upload(start, data, quads);
        slot.nimbus$set(start, quads);
        slot.nimbus$setFaces(faces);
        SectionData.set(slot.nimbus$section(), slot.nimbus$layer(), start, quads, faces, sprites);
        if (translucent && slot.nimbus$section() >= 0 && slot.nimbus$section() < SectionData.flatTranslucent.length) {
            SectionData.flatTranslucent[slot.nimbus$section()] = flatLayer;
        }
        if (data != original) {
            MemoryUtil.nmemFree(data);
        }
        if (order != null && !flatLayer) {
            slot.nimbus$setCopy(original);
        } else {
            MemoryUtil.nmemFree(original);
        }
    }

    // Negative-facing quads farthest-first along the axis is descending, positive-facing ascending. Front faces of the two
    // groups lie on opposite sides of the camera's plane and never overlap on screen, back faces are culled.
    private static int[] planarOrder(long data, int quads, Faces faces) {
        int[] offsets = faces.offsets();
        if (quads < 2 || offsets[Faces.GROUPS] != offsets[Faces.UNASSIGNED]) {
            return null;
        }
        int axis = -1;
        for (int group = 0; group < Faces.UNASSIGNED; group++) {
            if (offsets[group + 1] > offsets[group]) {
                if (axis >= 0 && axis != group >> 1) {
                    return null;
                }
                axis = group >> 1;
            }
        }
        if (axis < 0) {
            return null;
        }
        int positiveStart = offsets[axis * 2 + 1];
        long[] keys = new long[quads];
        for (int q = 0; q < quads; q++) {
            long at = data + (long) q * Arena.QUAD_BYTES;
            int a = MemoryUtil.memGetInt(at);
            int coord = axis == 0 ? a & 0xFFFF : axis == 1 ? a >>> 16 : MemoryUtil.memGetInt(at + 4) & 0xFFFF;
            boolean positive = q >= positiveStart;
            keys[q] = (positive ? 1L << 48 : 0L) | (long) (positive ? coord : 0xFFFF - coord) << 24 | q;
        }
        java.util.Arrays.sort(keys);
        int[] order = new int[quads];
        for (int i = 0; i < quads; i++) {
            order[i] = (int) (keys[i] & 0xFFFFFF);
        }
        return order;
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

    static void uploadSorted(TerrainSlot slot, long data, int quads) {
        if (arena != null && slot.nimbus$count() == quads) {
            arena.upload(slot.nimbus$start(), data, quads);
        }
    }

    public static void release(TerrainSlot slot) {
        if (slot.nimbus$count() > 0 && arena != null) {
            arena.release(slot.nimbus$start(), slot.nimbus$count());
        }
        slot.nimbus$set(0, 0);
        slot.nimbus$setFaces(null);
        SectionData.clear(slot.nimbus$section(), slot.nimbus$layer());
        if (slot.nimbus$section() >= 0 && slot.nimbus$section() < SectionData.flatTranslucent.length
                && slot.nimbus$layer() == layerIndex(RenderType.translucent())) {
            SectionData.flatTranslucent[slot.nimbus$section()] = false;
        }
        long copy = slot.nimbus$copy();
        if (copy != 0L) {
            TranslucentSort.retire(copy);
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

    private static boolean sort(SectionRenderDispatcher.RenderSection section, int translucent, RenderType layer, SectionRenderDispatcher dispatcher,
                                double x, double y, double z) {
        if (NimbusConfig.get().ownTranslucentSort) {
            TerrainSlot[] slots = ((SectionStamp) section).nimbus$slots();
            if (translucent >= 0 && translucent < slots.length && slots[translucent] != null) {
                TerrainSlot slot = slots[translucent];
                if (slot.nimbus$copy() != 0L) {
                    return TranslucentSort.schedule(slot, section.getOrigin(), x, y, z);
                }
            }
        }
        return section.resortTransparency(layer, dispatcher);
    }

    // True when every quad lies in one axis-aligned plane: such a layer looks the same in any order.
    private static boolean flat(long data, int quads) {
        if (quads <= 0) {
            return true;
        }
        int axis = -1;
        int value = 0;
        for (int q = 0; q < quads; q++) {
            long at = data + (long) q * Arena.QUAD_BYTES;
            int[] x = FLAT_X;
            int[] y = FLAT_Y;
            int[] z = FLAT_Z;
            for (int v = 0; v < 4; v++) {
                int a = MemoryUtil.memGetInt(at + v * 16L);
                x[v] = a & 0xFFFF;
                y[v] = a >>> 16;
                z[v] = MemoryUtil.memGetInt(at + v * 16L + 4) & 0xFFFF;
            }
            int quadAxis = x[0] == x[1] && x[1] == x[2] && x[2] == x[3] ? 0 : y[0] == y[1] && y[1] == y[2] && y[2] == y[3] ? 1
                    : z[0] == z[1] && z[1] == z[2] && z[2] == z[3] ? 2 : -1;
            if (quadAxis < 0) {
                return false;
            }
            int quadValue = quadAxis == 0 ? x[0] : quadAxis == 1 ? y[0] : z[0];
            if (axis < 0) {
                axis = quadAxis;
                value = quadValue;
            } else if (axis != quadAxis || value != quadValue) {
                return false;
            }
        }
        return true;
    }

    private static final int[] FLAT_X = new int[4];
    private static final int[] FLAT_Y = new int[4];
    private static final int[] FLAT_Z = new int[4];

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
        // nearby sections (camera within one section of them) always resort; the rest share a per-frame budget, nearest first
        int translucent = layerIndex(layer);
        int resorted = 0;
        int budget = moved ? 48 : 15;
        if (Traversal.enabled() && Visibility.allCount == visible.size() && translucent >= 0) {
            // own traversal: walk the id arrays and skip sections without translucent geometry
            int[] ids = Visibility.allIds;
            SectionRenderDispatcher.RenderSection[] sections = Visibility.allSections;
            int[] masks = SectionData.mask;
            boolean[] flat = SectionData.flatTranslucent;
            int[] origins = Origins.values();
            for (int i = 0, count = Visibility.allCount; i < count; i++) {
                int id = ids[i];
                if (id < 0 || id >= masks.length || (masks[id] >>> translucent & 1) == 0 || id < flat.length && flat[id] || id * 4 + 2 >= origins.length) {
                    continue;
                }
                int ox = origins[id * 4] >> 4;
                int oy = origins[id * 4 + 1] >> 4;
                int oz = origins[id * 4 + 2] >> 4;
                boolean near = Math.abs(ox - sx) <= 1 && Math.abs(oy - sy) <= 1 && Math.abs(oz - sz) <= 1;
                if (!near && (resorted >= budget || !(moved || ox == sx || oy == sy || oz == sz))) {
                    continue;
                }
                if (sort(sections[i], translucent, layer, dispatcher, x, y, z) && !near) {
                    resorted++;
                }
            }
            return;
        }
        for (SectionRenderDispatcher.RenderSection section : visible) {
            net.minecraft.core.BlockPos origin = section.getOrigin();
            int ox = SectionPos.blockToSectionCoord(origin.getX());
            int oy = SectionPos.blockToSectionCoord(origin.getY());
            int oz = SectionPos.blockToSectionCoord(origin.getZ());
            int id = ((SectionStamp) section).nimbus$id();
            if (id >= 0 && id < SectionData.flatTranslucent.length && SectionData.flatTranslucent[id]) {
                continue;
            }
            boolean near = Math.abs(ox - sx) <= 1 && Math.abs(oy - sy) <= 1 && Math.abs(oz - sz) <= 1;
            if (!near && (resorted >= budget || !(moved || section.isAxisAlignedWith(sx, sy, sz)))) {
                continue;
            }
            if (sort(section, translucent, layer, dispatcher, x, y, z) && !near) {
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
        TranslucentSort.drain();
        Origins.flush();
        if (builtFrame != frame) {
            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = ((LevelRendererAccessor) levelRenderer).nimbus$visibleSections();
            long started = BENCH ? System.nanoTime() : 0L;
            fogSetup();
            double mx = x - lastX;
            double my = y - lastY;
            double mz = z - lastZ;
            int patched = SectionData.flushPending(layerIndex(RenderType.translucent()));
            if (patched >= 0 && Visibility.valid && LISTS.length > 0 && Visibility.changes == lastStamp && SectionData.version == lastVersion
                    && mx * mx + my * my + mz * mz < REBUILD_DISTANCE * REBUILD_DISTANCE && fogLimit == lastFog && fogCylinder == lastCylinder) {
                if (patched > 0) {
                    patch(visible);
                } else {
                    reuse();
                    reused++;
                }
            } else {
                build(visible, x, y, z, null, 0, true, null, false);
                lastStamp = Visibility.valid ? Visibility.changes : -1;
                lastVersion = SectionData.version;
                lastX = x;
                lastY = y;
                lastZ = z;
                lastFog = fogLimit;
                lastCylinder = fogCylinder;
            }
            builtFrame = frame;
            occlusion = NimbusConfig.get().gpuOcclusion && HiZ.available();
            if (occlusion) {
                opaqueLayers();
                for (int index : OPAQUE) {
                    if (index >= 0 && index < LISTS.length) {
                        LISTS[index].uploadSecond();
                    }
                }
                HiZ.select(LISTS, OPAQUE, frame);
            } else {
                HiZ.culling = false;
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
                if (gpuFrustum()) {
                    GpuFrustum.cull(list, projection, modelView, x, y, z);
                }
                draw(layer, modelView, projection, list.offset, draws, x, y, z);
            }
        }
        if (layer == RenderType.solid()) {
            lastDraws = 0;
        }
        lastDraws += draws;
        layer.clearRenderState();
        if (layer == RenderType.cutout() && occlusion && HiZ.culling) {
            HiZ.capture(projection, modelView, x, y, z);
            HiZ.test(LISTS, OPAQUE, layerIndex(RenderType.translucent()));
            List<RenderType> layers = RenderType.chunkBufferLayers();
            for (int opaque : OPAQUE) {
                if (opaque < 0 || opaque >= LISTS.length) {
                    continue;
                }
                Commands list = LISTS[opaque];
                if (list.count > 0 && list.offset2 >= 0) {
                    RenderType opaqueLayer = layers.get(opaque);
                    opaqueLayer.setupRenderState();
                    draw(opaqueLayer, modelView, projection, list.offset2, list.count, x, y, z);
                    opaqueLayer.clearRenderState();
                }
            }
        }
        return true;
    }

    private static final int[] OPAQUE = {-1, -1, -1};
    private static boolean occlusion;

    private static void opaqueLayers() {
        OPAQUE[0] = layerIndex(RenderType.solid());
        OPAQUE[1] = layerIndex(RenderType.cutoutMipped());
        OPAQUE[2] = layerIndex(RenderType.cutout());
    }

    private static void reuse() {
        if (markedTick != Nimbus.ticks) {
            markedTick = Nimbus.ticks;
            AnimatedSprites.markBits(spritesSeen);
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

    // face groups and fog are decided with a margin, so the lists stay valid until the camera moves REBUILD_DISTANCE
    private static final float FACE_MARGIN = 0.5F;
    private static final double REBUILD_DISTANCE = 0.45;
    private static final float QUANT_MARGIN = FACE_MARGIN * 4.0F;

    private static final Commands SPLICE = new Commands();
    private static final int[] ONE = new int[1];

    // Cuts changed sections out of the lists and adds their new draws: opaque layers at the end,
    // translucent draws in place so the back-to-front order holds. Falls back to a full build.
    private static void patch(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible) {
        int translucent = layerIndex(RenderType.translucent());
        for (int l = 0; l < LISTS.length; l++) {
            if (l != translucent) {
                LISTS[l].remove(SectionData.removeMarks, SectionData.removeStamp);
            }
        }
        build(visible, lastX, lastY, lastZ, SectionData.buildIds, SectionData.buildCount, false, null, true);
        if (SectionData.spliceCount == 0 || translucent < 0 || translucent >= LISTS.length) {
            return;
        }
        for (int i = 0; i < SectionData.spliceCount; i++) {
            int id = SectionData.spliceIds[i];
            SPLICE.clear();
            ONE[0] = id;
            build(visible, lastX, lastY, lastZ, ONE, 1, false, SPLICE, false);
            if (!LISTS[translucent].splice(id, SPLICE)) {
                build(visible, lastX, lastY, lastZ, null, 0, true, null, false);
                return;
            }
        }
        LISTS[translucent].upload();
    }

    private static int groupStart(int group, int o0, int o1, int o2) {
        return switch (group) {
            case 0 -> 0;
            case 1 -> o0 & 0xFFFF;
            case 2 -> o0 >>> 16;
            case 3 -> o1 & 0xFFFF;
            case 4 -> o1 >>> 16;
            case 5 -> o2 & 0xFFFF;
            default -> o2 >>> 16;
        };
    }

    // fresh: every visible section from scratch; otherwise the given sections are appended to the current lists
    // translucentTarget: only the translucent layer, into that list; skipTranslucent: every layer but translucent
    private static void build(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible, double x, double y, double z, int[] only, int onlyCount, boolean fresh,
                              Commands translucentTarget, boolean skipTranslucent) {
        int layers = RenderType.chunkBufferLayers().size();
        if (LISTS.length != layers) {
            LISTS = new Commands[layers];
            for (int l = 0; l < layers; l++) {
                LISTS[l] = new Commands();
            }
        }
        int translucentIndex = layerIndex(RenderType.translucent());
        if (fresh) {
            for (int l = 0; l < layers; l++) {
                LISTS[l].clear();
            }
        }
        int[] records = SectionData.records;
        int[][] animatedSprites = SectionData.animated;
        boolean tracked = Visibility.valid;
        int[] visibleIds = Visibility.ids;
        float limit = fogLimit == Float.MAX_VALUE ? Float.MAX_VALUE : fogLimit + FACE_MARGIN;
        boolean cylinder = fogCylinder;
        int fogged = fresh ? 0 : fogCulled;
        boolean markSprites = markedTick != Nimbus.ticks;
        markedTick = Nimbus.ticks;
        long[] seen = spritesSeen;
        if (fresh) {
            java.util.Arrays.fill(seen, 0L);
        }
        if (setStamps.length < AnimatedSprites.setCount()) {
            setStamps = new int[Math.max(AnimatedSprites.setCount(), setStamps.length * 2)];
        }
        int[] setStamps = Terrain.setStamps;
        int buildStamp = fresh ? ++Terrain.buildStamp : Terrain.buildStamp;
        int[] source = only != null ? only : visibleIds;
        for (int i = 0, size = only != null ? onlyCount : tracked ? Visibility.count : visible.size(); i < size; i++) {
            int id = only != null || tracked ? source[i] : ((SectionStamp) visible.get(i)).nimbus$id();
            int head = id * SectionData.ID_STRIDE;
            if (head + SectionData.ID_STRIDE > records.length) {
                continue;
            }
            int bits = records[head + 3];
            if (bits == 0) {
                continue;
            }
            float cx = (float) (x - records[head]);
            float cy = (float) (y - records[head + 1]);
            float cz = (float) (z - records[head + 2]);
            float qx = (cx + 16.0F) * 4.0F;
            float qy = (cy + 16.0F) * 4.0F;
            float qz = (cz + 16.0F) * 4.0F;
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
                int at = head + SectionData.HEADER + l * SectionData.STRIDE;
                int flags = records[at + SectionData.FLAGS];
                if ((flags & SectionData.ANIMATED) != 0) {
                    int set = flags >>> SectionData.SET_SHIFT;
                    if (set == AnimatedSprites.NO_SET) {
                        int[] sprites = animatedSprites[key];
                        if (sprites != null) {
                            seen = addSprites(seen, sprites);
                        }
                    } else if (setStamps[set] != buildStamp) {
                        setStamps[set] = buildStamp;
                        seen = addSprites(seen, AnimatedSprites.set(set));
                    }
                }
                Commands list = LISTS[l];
                if (l == translucentIndex) {
                    if (skipTranslucent) {
                        continue;
                    }
                    if (translucentTarget != null) {
                        list = translucentTarget;
                    }
                } else if (translucentTarget != null) {
                    continue;
                }
                int base = records[at + SectionData.START];
                if ((flags & SectionData.GROUPED) == 0) {
                    list.add(base, records[at + SectionData.COUNT], id, MAX_QUADS_PER_DRAW);
                    continue;
                }
                int lo = records[at + SectionData.PLANES];
                int hi = records[at + SectionData.PLANES + 1];
                int shown = (qy < (lo & 0xFF) + QUANT_MARGIN ? 1 : 0)
                        | (qy > (lo >>> 8 & 0xFF) - QUANT_MARGIN ? 2 : 0)
                        | (qz < (lo >>> 16 & 0xFF) + QUANT_MARGIN ? 4 : 0)
                        | (qz > (lo >>> 24) - QUANT_MARGIN ? 8 : 0)
                        | (qx < (hi & 0xFF) + QUANT_MARGIN ? 16 : 0)
                        | (qx > (hi >>> 8 & 0xFF) - QUANT_MARGIN ? 32 : 0)
                        | 64;
                int o0 = records[at + SectionData.OFFSETS];
                int o1 = records[at + SectionData.OFFSETS + 1];
                int o2 = records[at + SectionData.OFFSETS + 2];
                int count = records[at + SectionData.COUNT];
                int present = flags >>> SectionData.GROUP_SHIFT & 0x7F;
                int runStart = -1;
                while (present != 0) {
                    int group = Integer.numberOfTrailingZeros(present);
                    present &= present - 1;
                    if ((shown >>> group & 1) != 0) {
                        if (runStart < 0) {
                            runStart = groupStart(group, o0, o1, o2);
                        }
                    } else if (runStart >= 0) {
                        list.add(base + runStart, groupStart(group, o0, o1, o2) - runStart, id, MAX_QUADS_PER_DRAW);
                        runStart = -1;
                    }
                }
                if (runStart >= 0) {
                    list.add(base + runStart, count - runStart, id, MAX_QUADS_PER_DRAW);
                }
            }
        }
        fogCulled = fogged;
        if (markSprites) {
            AnimatedSprites.markBits(seen);
        }
        if (fresh && translucentIndex >= 0) {
            LISTS[translucentIndex].reverse();
        }
        if (translucentTarget == null) {
            for (int l = 0; l < layers; l++) {
                LISTS[l].upload();
            }
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
        for (int page = 0; page < Arena.maxPages; page++) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, page, arena.buffer(page));
        }
        GL43.glBindVertexBuffer(0, Origins.buffer(), 0, 16);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, Stream.buffer());
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_SHORT, commandOffset, draws, 20);
        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        for (int page = 0; page < Arena.maxPages; page++) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, page, 0);
        }
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}

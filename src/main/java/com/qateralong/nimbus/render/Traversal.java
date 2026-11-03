package com.qateralong.nimbus.render;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ViewAreaAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ChunkTrackingView;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Section BFS (vanilla SectionOcclusionGraph rules) against a widened frustum. Like vanilla's full update it runs
// on its own thread when the camera changes section or the graph needs a refresh, and the previous result stays in
// use until it lands; only leaving the widened frustum searches in the frame. Every frame the current result is
// filtered by the real frustum using per-column section ranges.
public final class Traversal {
    private static final int[] DX = new int[6];
    private static final int[] DY = new int[6];
    private static final int[] DZ = new int[6];
    private static final int[] OPPOSITE = new int[6];
    private static final int[] ENTRY_FACES = new int[64];
    private static final float WIDEN = (float) Math.tan(Math.toRadians(10.0));
    // past half the margin a new search starts in the background, so it lands before the margin runs out
    private static final float INNER_WIDEN = (float) Math.tan(Math.toRadians(5.0));
    private static final float MARGIN = 4.0F;
    private static final float NEAR_WIDEN = (float) Math.tan(Math.toRadians(1.0));
    private static final float NEAR_MARGIN = 1.0F;
    private static final int EMPTY = 0x7FFF;

    static {
        for (Direction direction : Direction.values()) {
            int i = direction.ordinal();
            DX[i] = direction.getStepX();
            DY[i] = direction.getStepY();
            DZ[i] = direction.getStepZ();
            OPPOSITE[i] = direction.getOpposite().ordinal();
        }
        for (int set = 0; set < 64; set++) {
            int faces = 0;
            for (int i = 0; i < 6; i++) {
                if ((set & (1 << i)) != 0) {
                    faces |= 1 << OPPOSITE[i];
                }
            }
            ENTRY_FACES[set] = faces;
        }
    }

    private static final float[] real = new float[24];
    private static final float[] near = new float[16];
    private static double nearX;
    private static double nearY;
    private static double nearZ;
    private static final Vector4f plane = new Vector4f();
    private static final Vector3f[] rays = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    private static final int REJECTED = 1 << 12;
    private static final int FRAME_SHIFT = 13;
    private static final int FRAME_MASK = -1 << FRAME_SHIFT;
    private static final int MAX_FRAME = 1 << 18;
    private static final int REVISIT = 1 << 30;
    private static final Object GAINED_LOCK = new Object();
    private static int[] gained = new int[256];
    private static int gainedCount;
    // visibility masks by grid slot, shared by both graphs
    private static long[] slotMasks = new long[0];
    private static int[] gridIds = new int[0];
    private static SectionRenderDispatcher.RenderSection[] gridOwner;

    private static final boolean ASYNC = System.getProperty("nimbus.syncTraversal") == null;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Nimbus traversal");
        thread.setDaemon(true);
        return thread;
    });
    private static Graph current = new Graph();
    private static Graph spare = new Graph();
    private static Future<?> pending;
    private static Graph pendingGraph;
    // slots whose mask changed while a background search ran; checked against its result when it lands
    private static int[] touched = new int[256];
    private static int touchedCount;
    private static boolean installed;

    private static int[] realFrame = new int[0];
    private static short[] realLo = new short[0];
    private static short[] realHi = new short[0];
    private static boolean[] openedColumns = new boolean[0];

    private static boolean cached;
    private static int framesCached;
    private static final int LAZY_FRAMES = 20;
    // partial expansion is approximate like vanilla's, so a full search re-converges now and then after gains
    private static final int GAIN_REFRESH_FRAMES = 120;
    private static int filterFrame;

    private static long lastHash;
    private static int lastCount = -1;
    private static int ownChanges = -1;
    private static boolean lastGpu;

    public static volatile boolean smartCull = true;
    public static double cameraX;
    public static double cameraY;
    public static double cameraZ;

    public static int visited;
    public static int rebuilds;
    public static int inFrame;
    public static long nanos;
    private static long bfsNanos;

    private Traversal() {
    }

    public static boolean enabled() {
        return Terrain.active() && NimbusConfig.get().ownTraversal;
    }

    public static void collect(ViewArea viewArea, Frustum frustum, List<SectionRenderDispatcher.RenderSection> output) {
        long started = System.nanoTime();
        ViewAreaAccessor area = (ViewAreaAccessor) viewArea;
        SectionRenderDispatcher.RenderSection[] grid = viewArea.sections;
        int sizeX = area.nimbus$sizeX();
        int sizeY = area.nimbus$sizeY();
        int sizeZ = area.nimbus$sizeZ();
        int minSection = viewArea.getLevelHeightAccessor().getMinSection();
        int distance = viewArea.getViewDistance();
        int[] ids = gridIds(grid);
        int camSx = floor16(cameraX);
        int camSy = floor16(cameraY);
        int camSz = floor16(cameraZ);
        boolean cull = smartCull;
        Matrix4f matrix = frustum.matrix;
        for (int i = 0; i < 6; i++) {
            matrix.frustumPlane(i, plane);
            real[i * 4] = plane.x;
            real[i * 4 + 1] = plane.y;
            real[i * 4 + 2] = plane.z;
            real[i * 4 + 3] = plane.w;
        }
        var level = Minecraft.getInstance().level;
        var source = level == null ? null : level.getChunkSource();
        int chunks = source == null ? -1 : source.getLoadedChunksCount();
        framesCached++;
        rays(matrix);

        install(false);
        Graph g = current;
        boolean usable = usable(g, frustum, distance, cull, sizeX, sizeY);
        if (!usable && pending != null) {
            install(true);
            g = current;
            usable = usable(g, frustum, distance, cull, sizeX, sizeY);
        }
        boolean fresh = usable && camSx == g.sx && camSy == g.sy && camSz == g.sz && Origins.moves == g.moves
                && (SectionData.changed == g.changed || framesCached < LAZY_FRAMES)
                && (SectionData.gains == g.gains || framesCached < GAIN_REFRESH_FRAMES)
                && (ASYNC ? contains(g.inner, MARGIN * 0.5F, frustum.camX - g.originX, frustum.camY - g.originY, frustum.camZ - g.originZ) : true);
        if (!usable || !fresh && !ASYNC) {
            Graph next = spare;
            next.prepare(ids, source, chunks, sizeX, sizeY, sizeZ, minSection, distance, camSx, camSy, camSz, cull, frustum, WIDEN, MARGIN);
            next.run();
            install(next);
            inFrame++;
            g = current;
        } else if (!fresh && pending == null) {
            Graph next = spare;
            next.prepare(ids, source, chunks, sizeX, sizeY, sizeZ, minSection, distance, camSx, camSy, camSz, cull, frustum, WIDEN, MARGIN);
            pendingGraph = next;
            touchedCount = 0;
            pending = WORKER.submit(next::run);
        }

        boolean grew = false;
        if (chunks != g.chunks) {
            int before = g.count;
            g.loadChunks(source, chunks);
            g.extend();
            g.chunks = chunks;
            grew = g.count != before;
        }
        if (SectionData.graph != g.graph) {
            int before = g.count;
            g.extend();
            g.graph = SectionData.graph;
            grew |= g.count != before;
        }
        boolean rebuilt = installed;
        installed = false;
        boolean gpu = Terrain.gpuFrustum();
        if (rebuilt || grew || !Visibility.valid || Visibility.changes != ownChanges || gpu != lastGpu
                || !gpu && !contains(near, NEAR_MARGIN, frustum.camX - nearX, frustum.camY - nearY, frustum.camZ - nearZ)) {
            lastGpu = gpu;
            widen(near, NEAR_WIDEN, NEAR_MARGIN);
            nearX = frustum.camX;
            nearY = frustum.camY;
            nearZ = frustum.camZ;
            emit(g, nearX, nearY, nearZ, gpu);
        } else {
            Visibility.refresh();
            ownChanges = Visibility.changes;
        }
        if (gpu) {
            Visibility.cullBlockEntities(real, frustum.camX, frustum.camY, frustum.camZ);
        }
        if (output instanceof ObjectArrayList<SectionRenderDispatcher.RenderSection> list) {
            list.addElements(list.size(), Visibility.allSections, 0, Visibility.allCount);
        } else {
            for (int i = 0; i < Visibility.allCount; i++) {
                output.add(Visibility.allSections[i]);
            }
        }
        if (CHECK && ++checkFrame >= 240) {
            checkFrame = 0;
            check(ids, frustum, source, chunks, distance, minSection, sizeX, sizeY, sizeZ, camSx, camSy, camSz, cull);
        }
        long took = System.nanoTime() - started;
        nanos = ema(nanos, took);
    }

    private static boolean usable(Graph g, Frustum frustum, int distance, boolean cull, int sizeX, int sizeY) {
        return cached && Visibility.valid && g.distance == distance && g.cull == cull && g.sizeX == sizeX && g.sizeY == sizeY
                && contains(g.wide, MARGIN, frustum.camX - g.originX, frustum.camY - g.originY, frustum.camZ - g.originZ);
    }

    // Lands a finished background search; with wait, blocks until the running one finishes.
    private static void install(boolean wait) {
        Future<?> task = pending;
        if (task == null || !wait && !task.isDone()) {
            return;
        }
        Graph g = pendingGraph;
        pending = null;
        pendingGraph = null;
        try {
            task.get();
        } catch (Exception e) {
            com.qateralong.nimbus.Nimbus.LOGGER.warn("Nimbus: background section search failed", e);
            touchedCount = 0;
            return;
        }
        install(g);
    }

    private static void install(Graph g) {
        spare = current;
        current = g;
        cached = true;
        installed = true;
        framesCached = 0;
        rebuilds++;
        bfsNanos = ema(bfsNanos, g.nanos);
        int mark = g.visitFrame << FRAME_SHIFT;
        long[] masks = slotMasks;
        synchronized (GAINED_LOCK) {
            gainedCount = 0;
            for (int i = 0; i < touchedCount; i++) {
                int slot = touched[i];
                int bits = g.state[slot];
                if ((bits & FRAME_MASK) == mark && (bits & REJECTED) == 0 && (masks[slot] & ~g.usedMasks[slot]) != 0L) {
                    addGained(slot);
                }
            }
            if (gainedCount > 0) {
                g.graph = ~SectionData.graph;
            }
        }
        touchedCount = 0;
    }

    private static void addGained(int slot) {
        if (gainedCount == gained.length) {
            gained = Arrays.copyOf(gained, gainedCount * 2);
        }
        gained[gainedCount++] = slot;
    }

    private static void widen(float[] out, float widen, float margin) {
        float fx = real[16];
        float fy = real[17];
        float fz = real[18];
        float length = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (length > 0.0F) {
            fx /= length;
            fy /= length;
            fz /= length;
        }
        for (int i = 0; i < 4; i++) {
            float a = real[i * 4] + widen * fx;
            float b = real[i * 4 + 1] + widen * fy;
            float c = real[i * 4 + 2] + widen * fz;
            float scale = 1.0F / (float) Math.sqrt(a * a + b * b + c * c);
            out[i * 4] = a * scale;
            out[i * 4 + 1] = b * scale;
            out[i * 4 + 2] = c * scale;
            out[i * 4 + 3] = real[i * 4 + 3] * scale + margin;
        }
    }

    // Debug (-Dnimbus.traversalCheck): exact search without margins; every section it finds must be in the shown list.
    private static void check(int[] ids, Frustum frustum, Object source, int chunks, int distance, int minSection, int sizeX, int sizeY, int sizeZ,
                              int camSx, int camSy, int camSz, boolean cull) {
        install(true);
        Graph g = spare;
        g.prepare(ids, source, chunks, sizeX, sizeY, sizeZ, minSection, distance, camSx, camSy, camSz, cull, frustum, 0.0F, 0.0F);
        int exact = g.search();
        it.unimi.dsi.fastutil.ints.IntOpenHashSet shown = new it.unimi.dsi.fastutil.ints.IntOpenHashSet(Visibility.allCount * 2);
        for (int i = 0; i < Visibility.allCount; i++) {
            shown.add(Visibility.allIds[i]);
        }
        int missing = 0;
        int missingDrawn = 0;
        for (int i = 0; i < exact; i++) {
            int id = ids[g.slots[i]];
            if (!shown.contains(id)) {
                missing++;
                if (id < SectionData.mask.length && SectionData.mask[id] != 0) {
                    missingDrawn++;
                }
            }
        }
        com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus traversal check: exact {}, shown {}, missing {} ({} with geometry)",
                exact, Visibility.allCount, missing, missingDrawn);
    }

    private static final boolean CHECK = System.getProperty("nimbus.traversalCheck") != null;
    private static int checkFrame;

    private static void rays(Matrix4f matrix) {
        matrix.frustumRayDir(0.0F, 0.0F, rays[0]);
        matrix.frustumRayDir(1.0F, 0.0F, rays[1]);
        matrix.frustumRayDir(0.0F, 1.0F, rays[2]);
        matrix.frustumRayDir(1.0F, 1.0F, rays[3]);
    }

    private static boolean contains(float[] planes, float margin, double dx, double dy, double dz) {
        for (int i = 0; i < 4; i++) {
            float a = planes[i * 4];
            float b = planes[i * 4 + 1];
            float c = planes[i * 4 + 2];
            if (a * dx + b * dy + c * dz + planes[i * 4 + 3] < margin * 0.5F) {
                return false;
            }
            for (Vector3f ray : rays) {
                if (a * ray.x + b * ray.y + c * ray.z < 0.0F) {
                    return false;
                }
            }
        }
        return true;
    }

    // Section-y range [lo, hi] of a 16-wide column whose boxes pass every plane (same test as FrustumIntersection.testAab).
    private static int range(float[] planes, int count, double x0, double z0, double originY) {
        double lo = -1.0e9;
        double hi = 1.0e9;
        for (int i = 0; i < count; i++) {
            double a = planes[i * 4];
            double b = planes[i * 4 + 1];
            double c = planes[i * 4 + 2];
            double k = a * (a < 0.0 ? x0 : x0 + 16.0) + c * (c < 0.0 ? z0 : z0 + 16.0) + planes[i * 4 + 3];
            if (b > 1.0e-7) {
                lo = Math.max(lo, -k / b - 16.0);
            } else if (b < -1.0e-7) {
                hi = Math.min(hi, -k / b);
            } else if (k < -1.0e-3) {
                return EMPTY;
            }
        }
        if (lo > hi) {
            return EMPTY;
        }
        int sLo = (int) Math.ceil((lo + originY) / 16.0 - 1.0e-4);
        int sHi = (int) Math.floor((hi + originY) / 16.0 + 1.0e-4);
        sLo = Math.max(sLo, -4096);
        sHi = Math.min(sHi, 4096);
        return sLo > sHi ? EMPTY : (sLo & 0xFFFF) | sHi << 16;
    }

    private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);

    // Called on the render thread when a section's visibility mask changes; true if the current result read that section.
    public static boolean onMask(int id, long mask) {
        int[] slotOf = SectionData.slotOf;
        if (id >= slotOf.length) {
            return true;
        }
        int slot = slotOf[id];
        long[] masks = slotMasks;
        if (slot < 0 || slot >= masks.length) {
            return false;
        }
        masks[slot] = mask;
        if (pending != null) {
            if (touchedCount == touched.length) {
                touched = Arrays.copyOf(touched, touchedCount * 2);
            }
            touched[touchedCount++] = slot;
        }
        Graph g = current;
        if (slot >= g.state.length) {
            return false;
        }
        int bits = g.state[slot];
        // lost connections only leave extra sections in the current result, which is safe; the lazy path drops them
        if ((bits & FRAME_MASK) != g.visitFrame << FRAME_SHIFT || (bits & REJECTED) != 0 || (mask & ~g.usedMasks[slot]) == 0L) {
            return false;
        }
        synchronized (GAINED_LOCK) {
            addGained(slot);
        }
        return true;
    }

    // gpu: the GPU tests every draw against the frustum, so the whole widened search is shown
    private static void emit(Graph g, double fx, double fy, double fz, boolean gpu) {
        int size = g.side;
        int cells = size * size;
        if (realFrame.length < cells) {
            realFrame = new int[cells];
            realLo = new short[cells];
            realHi = new short[cells];
            filterFrame = 0;
        }
        if (++filterFrame == Integer.MAX_VALUE) {
            filterFrame = 1;
            Arrays.fill(realFrame, 0);
        }
        int now = filterFrame;
        int base = g.distance + 1;
        int camSx = g.sx;
        int camSz = g.sz;
        int minSection = g.minSection;
        int[] packedQueue = g.queue;
        int[] cachedIds = g.ids;
        int[] frames = realFrame;
        short[] lo = realLo;
        short[] hi = realHi;
        long hash = 1469598103934665603L;
        int count = 0;
        int skipped = 0;
        int total = g.count;
        for (int i = 0; i < total; i++) {
            int packed = packedQueue[i];
            int lx = packed & 0xFF;
            int lz = (packed >>> 8) & 0xFF;
            int c = lz * size + lx;
            if (gpu) {
                int id = cachedIds[i];
                hash = (hash ^ id) * 1099511628211L;
                filtered = grow(filtered, count);
                filtered[count++] = id;
                continue;
            }
            if (frames[c] != now) {
                frames[c] = now;
                int r = range(near, 4, ((camSx + lx - base) << 4) - fx, ((camSz + lz - base) << 4) - fz, fy);
                lo[c] = (short) (r == EMPTY ? EMPTY : r);
                hi[c] = (short) (r == EMPTY ? -EMPTY : r >> 16);
            }
            int ny = (packed >>> 16) + minSection;
            if (ny < lo[c] || ny > hi[c]) {
                hidden = grow(hidden, skipped);
                hidden[skipped++] = cachedIds[i];
                continue;
            }
            int id = cachedIds[i];
            hash = (hash ^ id) * 1099511628211L;
            filtered = grow(filtered, count);
            filtered[count++] = id;
        }
        SectionRenderDispatcher.RenderSection[] byId = SectionData.sections;
        boolean same = Visibility.valid && count == lastCount && hash == lastHash && Visibility.allCount == count && Visibility.changes == ownChanges;
        if (!same) {
            Visibility.begin();
            for (int i = 0; i < count; i++) {
                int id = filtered[i];
                Visibility.add(byId[id], id);
            }
        } else {
            Visibility.refresh();
        }
        // sections inside the widened search but off screen are queued for building after the visible ones
        int stampNow = Visibility.stamp;
        int[] prefetch = SectionData.prefetchFrame;
        int[] order = SectionData.order;
        for (int i = 0; i < skipped; i++) {
            int id = hidden[i];
            if (id < prefetch.length && id < order.length && SectionData.visibleFrame[id] != stampNow) {
                prefetch[id] = stampNow;
                order[id] = SectionData.PREFETCH | i;
            }
        }
        ownChanges = Visibility.changes;
        visited = total;
        lastCount = count;
        lastHash = hash;
    }

    private static int[] filtered = new int[4096];
    private static int[] hidden = new int[4096];

    private static int[] grow(int[] array, int index) {
        return index < array.length ? array : Arrays.copyOf(array, array.length * 2);
    }

    private static int[] gridIds(SectionRenderDispatcher.RenderSection[] grid) {
        if (grid != gridOwner || gridIds.length != grid.length) {
            if (pending != null) {
                try {
                    pending.get();
                } catch (Exception ignored) {
                }
                pending = null;
                pendingGraph = null;
            }
            touchedCount = 0;
            int[] ids = new int[grid.length];
            for (int i = 0; i < grid.length; i++) {
                ids[i] = ((SectionStamp) grid[i]).nimbus$id();
            }
            gridIds = ids;
            current.reset(grid.length);
            spare.reset(grid.length);
            long[] masks = new long[grid.length];
            int[] slotOf = SectionData.slotOf;
            Arrays.fill(slotOf, -1);
            long[] known = SectionData.visibility;
            for (int i = 0; i < grid.length; i++) {
                int id = ids[i];
                if (id >= 0 && id < slotOf.length) {
                    slotOf[id] = i;
                    masks[i] = known[id];
                }
            }
            slotMasks = masks;
            gridOwner = grid;
            cached = false;
        }
        return gridIds;
    }

    private static long ema(long old, long sample) {
        return old == 0 ? sample : (old * 31 + sample) / 32;
    }

    private static int floor16(double value) {
        return (int) Math.floor(value / 16.0);
    }

    public static String debug() {
        return String.format(java.util.Locale.ROOT, "traversal %d cached, %d shown, %d rebuilds (%d in frame), %.2f ms (bfs %.2f ms)",
                visited, lastCount, rebuilds, inFrame, nanos / 1e6, bfsNanos / 1e6);
    }

    // One search result and everything needed to extend it. The background search only touches its own graph,
    // the grid ids and the shared slot masks; everything else here is the render thread's.
    private static final class Graph {
        int[] state = new int[0];
        int[] packedOf = new int[0];
        // per slot: search frame << 3 | axes whose line of sight already passed in that search
        int[] rayMemo = new int[0];
        // mask each visited slot was expanded with in the last search, -1 when it ignored the mask
        long[] usedMasks = new long[0];
        int[] queue = new int[4096];
        int[] slots = new int[4096];
        int frame;
        int visitFrame = -1;

        int side;
        short[] wideLo = new short[0];
        short[] wideHi = new short[0];
        boolean[] neighbors = new boolean[0];
        boolean[] loaded = new boolean[0];
        int[] wrapX = new int[0];
        int[] wrapZ = new int[0];
        byte[] rangeTable = new byte[0];
        int rangeDistance = -1;
        Object loadedSource;
        int loadedChunks = -2;
        int loadedX;
        int loadedZ;
        int loadedSide;

        final float[] wide = new float[16];
        final float[] inner = new float[16];
        double originX;
        double originY;
        double originZ;
        double camX;
        double camY;
        double camZ;
        int[] grid = new int[0];
        int sizeX;
        int sizeY;
        int minSection;
        int sx;
        int sy;
        int sz;
        int distance = -1;
        boolean cull;
        int graph;
        int changed;
        int gains;
        int moves;
        int chunks;

        int count;
        int[] ids = new int[4096];
        long nanos;

        void reset(int length) {
            state = new int[length];
            packedOf = new int[length];
            rayMemo = new int[length];
            usedMasks = new long[length];
            frame = 0;
            visitFrame = -1;
            count = 0;
            distance = -1;
            loadedSource = null;
            loadedChunks = -2;
        }

        void prepare(int[] gridIds, Object source, int chunks, int sizeX, int sizeY, int sizeZ, int minSection, int distance, int camSx, int camSy,
                     int camSz, boolean cull, Frustum frustum, float widen, float margin) {
            this.grid = gridIds;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.minSection = minSection;
            this.distance = distance;
            this.sx = camSx;
            this.sy = camSy;
            this.sz = camSz;
            this.cull = cull;
            this.camX = cameraX;
            this.camY = cameraY;
            this.camZ = cameraZ;
            this.graph = SectionData.graph;
            this.changed = SectionData.changed;
            this.gains = SectionData.gains;
            this.moves = Origins.moves;
            this.chunks = chunks;
            widen(wide, widen, margin);
            widen(inner, INNER_WIDEN, margin * 0.5F);
            originX = frustum.camX;
            originY = frustum.camY;
            originZ = frustum.camZ;
            prepareColumns(source, chunks, sizeZ);
        }

        void run() {
            long started = System.nanoTime();
            int total = search();
            if (ids.length < total) {
                ids = new int[Math.max(total, ids.length * 2)];
            }
            int[] slotQueue = slots;
            int[] byGrid = grid;
            for (int i = 0; i < total; i++) {
                ids[i] = byGrid[slotQueue[i]];
            }
            count = total;
            nanos = System.nanoTime() - started;
        }

        private void prepareColumns(Object chunkSource, int chunks, int sizeZ) {
            int size = 2 * distance + 3;
            int cells = size * size;
            if (wideLo.length < cells) {
                wideLo = new short[cells];
                wideHi = new short[cells];
                neighbors = new boolean[cells];
                loaded = new boolean[cells];
            }
            if (wrapX.length < size) {
                wrapX = new int[size];
                wrapZ = new int[size];
            }
            side = size;
            int base = distance + 1;
            for (int i = 0; i < size; i++) {
                wrapX[i] = Math.floorMod(sx + i - base, sizeX);
                wrapZ[i] = Math.floorMod(sz + i - base, sizeZ);
            }
            var source = (net.minecraft.client.multiplayer.ClientChunkCache) chunkSource;
            if (chunkSource != loadedSource || chunks != loadedChunks || sx != loadedX || sz != loadedZ || size != loadedSide) {
                loadedSource = chunkSource;
                loadedChunks = chunks;
                loadedX = sx;
                loadedZ = sz;
                loadedSide = size;
                for (int lz = 0; lz < size; lz++) {
                    for (int lx = 0; lx < size; lx++) {
                        loaded[lz * size + lx] = source != null && source.hasChunk(sx + lx - base, sz + lz - base);
                    }
                }
            }
            if (rangeDistance != distance || rangeTable.length != cells) {
                rangeDistance = distance;
                rangeTable = new byte[cells];
                for (int lz = 0; lz < size; lz++) {
                    for (int lx = 0; lx < size; lx++) {
                        rangeTable[lz * size + lx] = (byte) (ChunkTrackingView.isInViewDistance(0, 0, distance, lx - base, lz - base) ? 1 : 0);
                    }
                }
            }
            // vanilla: abs(cameraY - sectionCenterY) <= distance * 16
            int yLo = Math.max(minSection, (int) Math.ceil((camY - distance * 16.0 - 8.0) / 16.0));
            int yHi = Math.min(minSection + sizeY - 1, (int) Math.floor((camY + distance * 16.0 - 8.0) / 16.0));
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    int c = lz * size + lx;
                    int lo = EMPTY;
                    int hi = -EMPTY;
                    if (rangeTable[c] != 0) {
                        int r = range(wide, 4, ((sx + lx - base) << 4) - originX, ((sz + lz - base) << 4) - originZ, originY);
                        if (r != EMPTY) {
                            lo = Math.max(yLo, (short) r);
                            hi = Math.min(yHi, r >> 16);
                        }
                        neighbors[c] = lx > 0 && lz > 0 && lx < size - 1 && lz < size - 1 && loaded[c - 1] && loaded[c + 1]
                                && loaded[c - size] && loaded[c + size];
                    }
                    wideLo[c] = (short) lo;
                    wideHi[c] = (short) hi;
                }
            }
        }

        int search() {
            if (++frame == MAX_FRAME) {
                frame = 1;
                Arrays.fill(state, 0);
                Arrays.fill(rayMemo, 0);
            }
            int now = frame;
            visitFrame = now;
            int mark = now << FRAME_SHIFT;
            int size = side;
            int base = distance + 1;
            int[] st = state;
            short[] lo = wideLo;
            short[] hi = wideHi;
            int[] wx = wrapX;
            int[] wz = wrapZ;
            int tail = 0;

            if (sy >= minSection && sy < minSection + sizeY) {
                int slot = (wz[base] * sizeY + sy - minSection) * sizeX + wx[base];
                st[slot] = mark;
                tail = push(tail, slot, base, sy - minSection, base);
            } else {
                boolean above = sy >= minSection + sizeY;
                int layer = above ? minSection + sizeY - 1 : minSection;
                int dir = (above ? Direction.DOWN : Direction.UP).ordinal();
                for (int ring = 0; ring <= distance; ring++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        for (int dz = -ring; dz <= ring; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                                continue;
                            }
                            int lx = base + dx;
                            int lz = base + dz;
                            int c = lz * size + lx;
                            if (rangeTable[c] == 0 || layer < lo[c] || layer > hi[c]) {
                                continue;
                            }
                            int slot = (wz[lz] * sizeY + layer - minSection) * sizeX + wx[lx];
                            if ((st[slot] & FRAME_MASK) == mark) {
                                continue;
                            }
                            st[slot] = mark | (1 << dir) << 6 | (1 << dir);
                            tail = push(tail, slot, lx, layer - minSection, lz);
                        }
                    }
                }
            }
            return propagate(0, tail, false);
        }

        // revisit: a found section that gains an entry direction is expanded again (its earlier expansion used fewer entries)
        private int propagate(int head, int tail, boolean revisit) {
            int now = frame;
            int mark = now << FRAME_SHIFT;
            int[] passed = rayMemo;
            int size = side;
            int base = distance + 1;
            int[] st = state;
            long[] masks = slotMasks;
            long[] used = usedMasks;
            short[] lo = wideLo;
            short[] hi = wideHi;
            int[] wx = wrapX;
            int[] wz = wrapZ;
            int sizeX = this.sizeX;
            int sizeY = this.sizeY;
            int minSection = this.minSection;
            int camSx = sx;
            int camSz = sz;
            boolean cull = this.cull;
            int centerX = (sx << 4) + 8;
            int centerY = (sy << 4) + 8;
            int centerZ = (sz << 4) + 8;
            while (head < tail) {
                int packed = queue[head];
                int cur = slots[head++];
                int lx = packed & 0xFF;
                int lz = (packed >>> 8) & 0xFF;
                int gy = (packed >>> 16) & 0x3FFF;
                int bits = st[cur];
                int moved = (bits >>> 6) & 63;
                int from = bits & 63;
                long mask = cull && from != 0 ? masks[cur] : -1L;
                used[cur] = mask;
                int entries = ENTRY_FACES[from];
                int ox = (camSx + lx - base) << 4;
                int oy = (gy + minSection) << 4;
                int oz = (camSz + lz - base) << 4;
                boolean far = cull && (Math.abs(ox - centerX) > 60 || Math.abs(oy - centerY) > 60 || Math.abs(oz - centerZ) > 60);
                for (int d = 0; d < 6; d++) {
                    if (cull && (moved & (1 << OPPOSITE[d])) != 0) {
                        continue;
                    }
                    if (mask != -1L && ((mask >>> (d * 6)) & entries) == 0) {
                        continue;
                    }
                    int nx = lx + DX[d];
                    int nz = lz + DZ[d];
                    int ngy = gy + DY[d];
                    int c = nz * size + nx;
                    int ny = ngy + minSection;
                    if (ny < lo[c] || ny > hi[c]) {
                        continue;
                    }
                    int slot = (wz[nz] * sizeY + ngy) * sizeX + wx[nx];
                    int seen = st[slot];
                    boolean visited = (seen & FRAME_MASK) == mark;
                    if (visited && (seen & (REJECTED | 1 << d)) != 0) {
                        continue;
                    }
                    if (far) {
                        int axis = d >> 1;
                        int memo = passed[slot];
                        if (memo >>> 3 != now || (memo >>> axis & 1) == 0) {
                            if (!lineOfSight(d, ox + (DX[d] << 4), oy + (DY[d] << 4), oz + (DZ[d] << 4), centerX, centerY, centerZ, mark)) {
                                continue;
                            }
                            passed[slot] = (memo >>> 3 == now ? memo : now << 3) | 1 << axis;
                        }
                    }
                    if (visited) {
                        st[slot] = seen | (1 << d);
                        if (revisit) {
                            tail = pushRevisit(tail, slot);
                        }
                        continue;
                    }
                    // vanilla hasAllNeighbors(): side chunks only matter beyond 24 blocks
                    if (!neighbors[c]) {
                        double ddx = ((camSx + nx - base) << 4) + 8 - camX;
                        double ddy = (ny << 4) + 8 - camY;
                        double ddz = ((camSz + nz - base) << 4) + 8 - camZ;
                        if (ddx * ddx + ddy * ddy + ddz * ddz > 576.0) {
                            st[slot] = mark | REJECTED;
                            continue;
                        }
                    }
                    st[slot] = mark | (moved | (1 << d)) << 6 | (1 << d);
                    tail = push(tail, slot, nx, ngy, nz);
                }
            }
            return tail;
        }

        // Chunks arrived or left since this search: columns whose four side chunks are now all loaded drop their
        // rejections, and found sections next to them are expanded again. Lost chunks only leave extra sections.
        void loadChunks(Object chunkSource, int chunks) {
            var source = (net.minecraft.client.multiplayer.ClientChunkCache) chunkSource;
            int size = side;
            int base = distance + 1;
            boolean[] wasLoaded = loaded;
            boolean[] hadNeighbors = neighbors;
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    wasLoaded[lz * size + lx] = source != null && source.hasChunk(sx + lx - base, sz + lz - base);
                }
            }
            loadedChunks = chunks;
            int[] st = state;
            int mark = frame << FRAME_SHIFT;
            int[] wx = wrapX;
            int[] wz = wrapZ;
            boolean any = false;
            boolean[] opened = openedColumns.length < size * size ? (openedColumns = new boolean[size * size]) : openedColumns;
            Arrays.fill(opened, 0, size * size, false);
            for (int lz = 1; lz < size - 1; lz++) {
                for (int lx = 1; lx < size - 1; lx++) {
                    int c = lz * size + lx;
                    if (rangeTable[c] == 0) {
                        continue;
                    }
                    boolean now = wasLoaded[c - 1] && wasLoaded[c + 1] && wasLoaded[c - size] && wasLoaded[c + size];
                    if (now && !hadNeighbors[c]) {
                        opened[c] = true;
                        any = true;
                        for (int gy = 0; gy < sizeY; gy++) {
                            int slot = (wz[lz] * sizeY + gy) * sizeX + wx[lx];
                            if ((st[slot] & FRAME_MASK) == mark && (st[slot] & REJECTED) != 0) {
                                st[slot] = 0;
                            }
                        }
                    }
                    hadNeighbors[c] = now;
                }
            }
            if (!any) {
                return;
            }
            int[] packedQueue = queue;
            int[] slotQueue = slots;
            synchronized (GAINED_LOCK) {
                for (int i = 0; i < count; i++) {
                    int packed = packedQueue[i];
                    int c = (packed >>> 8 & 0xFF) * size + (packed & 0xFF);
                    if (opened[c - 1] || opened[c + 1] || opened[c - size] || opened[c + size]) {
                        addGained(slotQueue[i]);
                    }
                }
            }
        }

        // Like vanilla's partial update: sections that gained connections are expanded again on top of this result.
        void extend() {
            int start = count;
            int tail = start;
            synchronized (GAINED_LOCK) {
                for (int i = 0; i < gainedCount; i++) {
                    int slot = gained[i];
                    if (tail == queue.length) {
                        queue = Arrays.copyOf(queue, tail * 2);
                        slots = Arrays.copyOf(slots, tail * 2);
                    }
                    queue[tail] = packedOf[slot] | REVISIT;
                    slots[tail++] = slot;
                }
                gainedCount = 0;
            }
            if (tail == start) {
                return;
            }
            tail = propagate(start, tail, true);
            int[] packedQueue = queue;
            int[] slotQueue = slots;
            int total = start;
            for (int i = start; i < tail; i++) {
                if ((packedQueue[i] & REVISIT) == 0) {
                    packedQueue[total] = packedQueue[i];
                    slotQueue[total++] = slotQueue[i];
                }
            }
            if (ids.length < total) {
                ids = Arrays.copyOf(ids, Math.max(total, ids.length * 2));
            }
            for (int i = start; i < total; i++) {
                ids[i] = grid[slotQueue[i]];
            }
            count = total;
        }

        // Vanilla's far-section rule: walking from the neighbour towards the camera must only cross sections already reached.
        private boolean lineOfSight(int d, int nx, int ny, int nz, int centerX, int centerY, int centerZ, int mark) {
            boolean axisX = d >= 4;
            boolean axisY = d < 2;
            boolean axisZ = !axisX && !axisY;
            double vx = nx + ((axisX ? centerX > nx : centerX < nx) ? 16 : 0);
            double vy = ny + ((axisY ? centerY > ny : centerY < ny) ? 16 : 0);
            double vz = nz + ((axisZ ? centerZ > nz : centerZ < nz) ? 16 : 0);
            double dx = camX - vx;
            double dy = camY - vy;
            double dz = camZ - vz;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-4) {
                return true;
            }
            double scale = CEILED_SECTION_DIAGONAL / length;
            double stepX = dx * scale;
            double stepY = dy * scale;
            double stepZ = dz * scale;
            double minY = minSection << 4;
            double maxY = (minSection + sizeY) << 4;
            int size = side;
            int base = distance + 1;
            int[] st = state;
            int[] wx = wrapX;
            int[] wz = wrapZ;
            while (dx * dx + dy * dy + dz * dz > 3600.0) {
                vx += stepX;
                vy += stepY;
                vz += stepZ;
                dx = camX - vx;
                dy = camY - vy;
                dz = camZ - vz;
                if (vy > maxY || vy < minY) {
                    return true;
                }
                int lx = ((int) Math.floor(vx) >> 4) - sx + base;
                int lz = ((int) Math.floor(vz) >> 4) - sz + base;
                int gy = ((int) Math.floor(vy) >> 4) - minSection;
                if (lx < 0 || lz < 0 || lx >= size || lz >= size || gy < 0 || gy >= sizeY) {
                    return false;
                }
                int bits = st[(wz[lz] * sizeY + gy) * sizeX + wx[lx]];
                if ((bits & FRAME_MASK) != mark || (bits & REJECTED) != 0) {
                    return false;
                }
            }
            return true;
        }

        private int pushRevisit(int tail, int slot) {
            if (tail == queue.length) {
                queue = Arrays.copyOf(queue, tail * 2);
                slots = Arrays.copyOf(slots, tail * 2);
            }
            queue[tail] = packedOf[slot] | REVISIT;
            slots[tail] = slot;
            return tail + 1;
        }

        private int push(int tail, int slot, int lx, int gy, int lz) {
            if (tail == queue.length) {
                queue = Arrays.copyOf(queue, tail * 2);
                slots = Arrays.copyOf(slots, tail * 2);
            }
            int packed = lx | lz << 8 | gy << 16;
            queue[tail] = packed;
            slots[tail] = slot;
            packedOf[slot] = packed;
            return tail + 1;
        }
    }
}

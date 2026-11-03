package com.qateralong.nimbus.render;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ViewAreaAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ChunkTrackingView;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Section BFS (vanilla SectionOcclusionGraph rules) against a widened frustum, rerun only when the camera
// leaves the margin, changes section, or the graph changes; every frame the cached result is filtered by
// the real frustum using per-column section ranges.
public final class Traversal {
    private static final int[] DX = new int[6];
    private static final int[] DY = new int[6];
    private static final int[] DZ = new int[6];
    private static final int[] OPPOSITE = new int[6];
    private static final int[] ENTRY_FACES = new int[64];
    private static final float WIDEN = (float) Math.tan(Math.toRadians(10.0));
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
    private static final float[] wide = new float[16];
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
    private static int[] state = new int[0];
    private static long[] slotMasks = new long[0];
    // mask each visited slot was expanded with in the last search, -1 when it ignored the mask
    private static long[] usedMasks = new long[0];
    private static volatile int visitFrame = -1;
    private static int[] queue = new int[4096];
    private static int[] slots = new int[4096];
    private static int[] gridIds = new int[0];
    private static SectionRenderDispatcher.RenderSection[] gridOwner;
    private static int frame;

    private static int side;
    private static short[] wideLo = new short[0];
    private static short[] wideHi = new short[0];
    private static boolean[] neighbors = new boolean[0];
    private static boolean[] loaded = new boolean[0];
    private static int[] realFrame = new int[0];
    private static short[] realLo = new short[0];
    private static short[] realHi = new short[0];
    private static int[] wrapX = new int[0];
    private static int[] wrapZ = new int[0];
    private static byte[] rangeTable = new byte[0];
    private static int rangeDistance = -1;

    private static Object loadedSource;
    private static int loadedChunks = -2;
    private static int loadedX;
    private static int loadedZ;
    private static int loadedSide;

    private static boolean cached;
    private static int cachedCount;
    private static int keySx;
    private static int keySy;
    private static int keySz;
    private static int keyGraph;
    private static int keyChanged;
    private static int framesCached;
    private static final int LAZY_FRAMES = 20;
    // new connections wait up to this many frames, vanilla's async graph lags about as much
    private static final int GAIN_FRAMES = 3;
    private static int keyMoves;
    private static int keyChunks;
    private static int keyDistance;
    private static boolean keyCull;
    private static double originX;
    private static double originY;
    private static double originZ;
    private static int filterFrame;

    private static long lastHash;
    private static int lastCount = -1;
    private static int ownChanges = -1;

    public static volatile boolean smartCull = true;
    public static double cameraX;
    public static double cameraY;
    public static double cameraZ;

    public static int visited;
    public static int rebuilds;
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
        int graph = SectionData.graph;
        int changed = SectionData.changed;
        framesCached++;
        int moves = Origins.moves;

        rays(matrix);
        boolean valid = cached && Visibility.valid && camSx == keySx && camSy == keySy && camSz == keySz && (graph == keyGraph || framesCached < GAIN_FRAMES)
                && moves == keyMoves && chunks == keyChunks && (changed == keyChanged || framesCached < LAZY_FRAMES) && distance == keyDistance && cull == keyCull
                && contains(wide, MARGIN, frustum.camX - originX, frustum.camY - originY, frustum.camZ - originZ);
        if (!valid) {
            long b0 = System.nanoTime();
            widen(wide, WIDEN, MARGIN);
            originX = frustum.camX;
            originY = frustum.camY;
            originZ = frustum.camZ;
            graph = SectionData.graph;
            changed = SectionData.changed;
            prepareColumns(source, chunks, camSx, camSy, camSz, distance, minSection, sizeX, sizeY, sizeZ);
            cachedCount = search(sizeX, sizeY, minSection, distance, camSx, camSy, camSz, cull);
            if (cachedIds.length < cachedCount) {
                cachedIds = new int[Math.max(cachedCount, cachedIds.length * 2)];
            }
            int[] slotQueue = slots;
            for (int i = 0; i < cachedCount; i++) {
                cachedIds[i] = ids[slotQueue[i]];
            }
            cached = true;
            keySx = camSx;
            keySy = camSy;
            keySz = camSz;
            keyGraph = graph;
            keyChanged = changed;
            framesCached = 0;
            keyMoves = moves;
            keyChunks = chunks;
            keyDistance = distance;
            keyCull = cull;
            rebuilds++;
            bfsNanos = ema(bfsNanos, System.nanoTime() - b0);
        }
        if (!valid || !Visibility.valid || Visibility.changes != ownChanges
                || !contains(near, NEAR_MARGIN, frustum.camX - nearX, frustum.camY - nearY, frustum.camZ - nearZ)) {
            widen(near, NEAR_WIDEN, NEAR_MARGIN);
            nearX = frustum.camX;
            nearY = frustum.camY;
            nearZ = frustum.camZ;
            emit(ids, distance, minSection, camSx, camSz, nearX, nearY, nearZ);
        } else {
            Visibility.refresh();
            ownChanges = Visibility.changes;
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
        widen(wide, 0.0F, 0.0F);
        originX = frustum.camX;
        originY = frustum.camY;
        originZ = frustum.camZ;
        prepareColumns(source, chunks, camSx, camSy, camSz, distance, minSection, sizeX, sizeY, sizeZ);
        int exact = search(sizeX, sizeY, minSection, distance, camSx, camSy, camSz, cull);
        it.unimi.dsi.fastutil.ints.IntOpenHashSet shown = new it.unimi.dsi.fastutil.ints.IntOpenHashSet(Visibility.allCount * 2);
        for (int i = 0; i < Visibility.allCount; i++) {
            shown.add(Visibility.allIds[i]);
        }
        int missing = 0;
        int missingDrawn = 0;
        for (int i = 0; i < exact; i++) {
            int id = ids[slots[i]];
            if (!shown.contains(id)) {
                missing++;
                if (id < SectionData.mask.length && SectionData.mask[id] != 0) {
                    missingDrawn++;
                }
            }
        }
        com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus traversal check: exact {}, shown {}, missing {} ({} with geometry)",
                exact, Visibility.allCount, missing, missingDrawn);
        cached = false;
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

    private static void prepareColumns(Object chunkSource, int chunks, int camSx, int camSy, int camSz, int distance, int minSection,
                                       int sizeX, int sizeY, int sizeZ) {
        int size = 2 * distance + 3;
        int cells = size * size;
        if (wideLo.length < cells) {
            wideLo = new short[cells];
            wideHi = new short[cells];
            neighbors = new boolean[cells];
            loaded = new boolean[cells];
            realFrame = new int[cells];
            realLo = new short[cells];
            realHi = new short[cells];
        }
        if (wrapX.length < size) {
            wrapX = new int[size];
            wrapZ = new int[size];
        }
        side = size;
        int base = distance + 1;
        for (int i = 0; i < size; i++) {
            wrapX[i] = Math.floorMod(camSx + i - base, sizeX);
            wrapZ[i] = Math.floorMod(camSz + i - base, sizeZ);
        }
        var source = (net.minecraft.client.multiplayer.ClientChunkCache) chunkSource;
        if (chunkSource != loadedSource || chunks != loadedChunks || camSx != loadedX || camSz != loadedZ || size != loadedSide) {
            loadedSource = chunkSource;
            loadedChunks = chunks;
            loadedX = camSx;
            loadedZ = camSz;
            loadedSide = size;
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    loaded[lz * size + lx] = source != null && source.hasChunk(camSx + lx - base, camSz + lz - base);
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
        double camY = cameraY;
        int yLo = Math.max(minSection, (int) Math.ceil((camY - distance * 16.0 - 8.0) / 16.0));
        int yHi = Math.min(minSection + sizeY - 1, (int) Math.floor((camY + distance * 16.0 - 8.0) / 16.0));
        for (int lz = 0; lz < size; lz++) {
            for (int lx = 0; lx < size; lx++) {
                int c = lz * size + lx;
                realFrame[c] = 0;
                int lo = EMPTY;
                int hi = -EMPTY;
                if (rangeTable[c] != 0) {
                    int r = range(wide, 4, ((camSx + lx - base) << 4) - originX, ((camSz + lz - base) << 4) - originZ, originY);
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

    private static int search(int sizeX, int sizeY, int minSection, int distance, int camSx, int camSy, int camSz, boolean cull) {
        if (++frame == MAX_FRAME) {
            frame = 1;
            Arrays.fill(state, 0);
        }
        int now = frame;
        visitFrame = now;
        int mark = now << FRAME_SHIFT;
        int size = side;
        int base = distance + 1;
        int[] st = state;
        long[] masks = slotMasks;
        long[] used = usedMasks;
        short[] lo = wideLo;
        short[] hi = wideHi;
        int[] wx = wrapX;
        int[] wz = wrapZ;
        double camX = cameraX;
        double camY = cameraY;
        double camZ = cameraZ;
        int tail = 0;

        if (camSy >= minSection && camSy < minSection + sizeY) {
            int slot = (wz[base] * sizeY + camSy - minSection) * sizeX + wx[base];
            st[slot] = mark;
            tail = push(tail, slot, base, camSy - minSection, base);
        } else {
            boolean above = camSy >= minSection + sizeY;
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

        int head = 0;
        while (head < tail) {
            int packed = queue[head];
            int cur = slots[head++];
            int lx = packed & 0xFF;
            int lz = (packed >>> 8) & 0xFF;
            int gy = packed >>> 16;
            int bits = st[cur];
            int moved = (bits >>> 6) & 63;
            int from = bits & 63;
            long mask = cull && from != 0 ? masks[cur] : -1L;
            used[cur] = mask;
            int entries = ENTRY_FACES[from];
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
                if ((seen & FRAME_MASK) == mark) {
                    if ((seen & REJECTED) == 0) {
                        st[slot] = seen | (1 << d);
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

    // Called when a section's visibility mask changes; true if the cached search read that section.
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
        int bits = state[slot];
        // lost connections only leave extra sections in the cached result, which is safe; the lazy path drops them
        return (bits & FRAME_MASK) == visitFrame << FRAME_SHIFT && (bits & REJECTED) == 0 && (mask & ~usedMasks[slot]) != 0L;
    }

    private static void emit(int[] ids, int distance, int minSection, int camSx, int camSz, double fx, double fy, double fz) {
        if (++filterFrame == Integer.MAX_VALUE) {
            filterFrame = 1;
            Arrays.fill(realFrame, 0);
        }
        int now = filterFrame;
        int size = side;
        int base = distance + 1;
        int[] packedQueue = queue;
        int[] cached = cachedIds;
        int[] frames = realFrame;
        short[] lo = realLo;
        short[] hi = realHi;
        long hash = 1469598103934665603L;
        int count = 0;
        int skipped = 0;
        int total = cachedCount;
        for (int i = 0; i < total; i++) {
            int packed = packedQueue[i];
            int lx = packed & 0xFF;
            int lz = (packed >>> 8) & 0xFF;
            int c = lz * size + lx;
            if (frames[c] != now) {
                frames[c] = now;
                int r = range(near, 4, ((camSx + lx - base) << 4) - fx, ((camSz + lz - base) << 4) - fz, fy);
                lo[c] = (short) (r == EMPTY ? EMPTY : r);
                hi[c] = (short) (r == EMPTY ? -EMPTY : r >> 16);
            }
            int ny = (packed >>> 16) + minSection;
            if (ny < lo[c] || ny > hi[c]) {
                hidden = grow(hidden, skipped);
                hidden[skipped++] = cached[i];
                continue;
            }
            int id = cached[i];
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
    private static int[] cachedIds = new int[4096];

    private static int[] grow(int[] array, int index) {
        return index < array.length ? array : Arrays.copyOf(array, array.length * 2);
    }

    private static int push(int tail, int slot, int lx, int gy, int lz) {
        if (tail == queue.length) {
            queue = Arrays.copyOf(queue, tail * 2);
            slots = Arrays.copyOf(slots, tail * 2);
        }
        queue[tail] = lx | lz << 8 | gy << 16;
        slots[tail] = slot;
        return tail + 1;
    }

    private static int[] gridIds(SectionRenderDispatcher.RenderSection[] grid) {
        if (grid != gridOwner || gridIds.length != grid.length) {
            int[] ids = new int[grid.length];
            for (int i = 0; i < grid.length; i++) {
                ids[i] = ((SectionStamp) grid[i]).nimbus$id();
            }
            gridIds = ids;
            state = new int[grid.length];
            frame = 0;
            visitFrame = -1;
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
            usedMasks = new long[grid.length];
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
        return String.format(java.util.Locale.ROOT, "traversal %d cached, %d shown, %d rebuilds, %.2f ms (bfs %.2f ms)",
                visited, lastCount, rebuilds, nanos / 1e6, bfsNanos / 1e6);
    }
}

package com.qateralong.nimbus.render;

import java.util.Arrays;

public final class SectionData {
    static final int LAYERS = 8;
    static int[] mask = new int[4096];
    static boolean[] blockEntities = new boolean[4096];
    static final int START = 0;
    static final int COUNT = 1;
    static final int FLAGS = 2;
    // 32 bytes per layer: planes as bytes in quarter blocks (rounded outward), group offsets 1..6 as shorts
    static final int PLANES = 3;
    static final int OFFSETS = 5;
    static final int STRIDE = 8;
    // per id: origin x, y, z and the layer mask, then LAYERS records, so drawing a section touches one region
    static final int HEADER = 4;
    static final int ID_STRIDE = HEADER + LAYERS * STRIDE;
    static final int GROUPED = 1;
    static final int ANIMATED = 2;
    static final int GROUP_SHIFT = 8;
    static final int SET_SHIFT = 16;
    static int[] records = new int[4096 * ID_STRIDE];
    public static boolean[] dirty = new boolean[4096];
    public static int[] order = new int[4096];
    public static net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection[] sections = new net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection[4096];
    public static int[] dirtyIds = new int[1024];
    public static int dirtyListed;
    public static int dirtyCount;
    static int[][] animated = new int[4096 * LAYERS][];
    public static int version;
    public static volatile int membership;
    public static long[] visibility = new long[4096];
    public static volatile int graph;
    public static volatile int changed;
    public static volatile int gains;
    public static int[] slotOf = filled(4096);
    public static int[] visibleFrame = new int[4096];
    public static int[] prefetchFrame = new int[4096];
    public static boolean[] flatTranslucent = new boolean[4096];
    public static final int PREFETCH = 1 << 30;

    public static void setVisibility(int id, net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection compiled) {
        if (id < 0) {
            return;
        }
        ensure(id);
        long mask = 0L;
        if (compiled != null && compiled != net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
            net.minecraft.core.Direction[] directions = net.minecraft.core.Direction.values();
            for (int leave = 0; leave < 6; leave++) {
                for (int entry = 0; entry < 6; entry++) {
                    if (compiled.facesCanSeeEachother(directions[entry], directions[leave])) {
                        mask |= 1L << (leave * 6 + entry);
                    }
                }
            }
        }
        long old = visibility[id];
        if (old != mask) {
            visibility[id] = mask;
            // gains are expanded by the traversal right away, only lost connections need the lazy full search
            if ((old & ~mask) != 0L) {
                changed++;
            }
            if ((mask & ~old) != 0L) {
                gains++;
            }
            if (Traversal.onMask(id, mask)) {
                graph++;
            }
        }
    }

    private SectionData() {
    }

    private static int[] filled(int size) {
        int[] array = new int[size];
        Arrays.fill(array, -1);
        return array;
    }

    static void reserve(int id) {
        ensure(id);
    }

    public static void register(int id, net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section) {
        ensure(id);
        sections[id] = section;
        visibleFrame[id] = -1;
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> LATER = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static boolean[] inList = new boolean[4096];
    public static int recovered;

    // Vanilla marks sections dirty from build threads too (a cancelled build); the lists here are render-thread only.
    public static boolean later(Runnable task) {
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            return false;
        }
        LATER.add(task);
        return true;
    }

    public static void drain() {
        for (Runnable task; (task = LATER.poll()) != null; ) {
            task.run();
        }
    }

    public static void setDirty(int id, boolean value) {
        if (id < 0 || id >= dirty.length) {
            return;
        }
        if (dirty[id] != value) {
            dirty[id] = value;
            dirtyCount += value ? 1 : -1;
        }
        if (value && !inList[id]) {
            if (dirtyListed == dirtyIds.length) {
                compactDirty();
                if (dirtyListed * 2 > dirtyIds.length) {
                    dirtyIds = Arrays.copyOf(dirtyIds, dirtyIds.length * 2);
                }
            }
            inList[id] = true;
            dirtyIds[dirtyListed++] = id;
        }
    }

    public static boolean tracked(int id) {
        return id >= 0 && id < dirty.length && dirty[id] && inList[id];
    }

    public static void compactDirty() {
        int kept = 0;
        for (int i = 0; i < dirtyListed; i++) {
            int id = dirtyIds[i];
            if (dirty[id] && inList[id]) {
                dirtyIds[kept++] = id;
                inList[id] = false;
            } else {
                inList[id] = false;
            }
        }
        for (int i = 0; i < kept; i++) {
            inList[dirtyIds[i]] = true;
        }
        dirtyListed = kept;
    }

    public static void setBlockEntities(int id, boolean present) {
        if (id >= 0 && id < blockEntities.length && blockEntities[id] != present) {
            blockEntities[id] = present;
            if (listed(id)) {
                membership++;
            }
        }
    }

    private static void ensure(int id) {
        if (id < mask.length) {
            return;
        }
        int size = Math.max(mask.length * 2, id + 1);
        mask = Arrays.copyOf(mask, size);
        blockEntities = Arrays.copyOf(blockEntities, size);
        records = Arrays.copyOf(records, size * ID_STRIDE);
        dirty = Arrays.copyOf(dirty, size);
        inList = Arrays.copyOf(inList, size);
        order = Arrays.copyOf(order, size);
        sections = Arrays.copyOf(sections, size);
        animated = Arrays.copyOf(animated, size * LAYERS);
        visibility = Arrays.copyOf(visibility, size);
        visibleFrame = Arrays.copyOf(visibleFrame, size);
        prefetchFrame = Arrays.copyOf(prefetchFrame, size);
        flatTranslucent = Arrays.copyOf(flatTranslucent, size);
        appendRound = Arrays.copyOf(appendRound, size);
        queuedMask = Arrays.copyOf(queuedMask, size);
        removeMarks = Arrays.copyOf(removeMarks, size);
        int old = slotOf.length;
        slotOf = Arrays.copyOf(slotOf, size);
        Arrays.fill(slotOf, old, size, -1);
    }

    static void set(int id, int layer, int first, int quads, Faces faces, int[] sprites) {
        if (id < 0 || layer < 0 || layer >= LAYERS) {
            return;
        }
        ensure(id);
        boolean shown = listed(id);
        int before = mask[id];
        int key = id * LAYERS + layer;
        int at = id * ID_STRIDE + HEADER + layer * STRIDE;
        int[] record = records;
        record[at + START] = first;
        record[at + COUNT] = quads;
        int flags = sprites != null ? ANIMATED | AnimatedSprites.setId(sprites) << SET_SHIFT : 0;
        animated[key] = sprites;
        if (faces != null && quads <= 0xFFFF) {
            int[] bounds = faces.offsets();
            int present = 0;
            for (int g = 0; g < Faces.GROUPS; g++) {
                if (bounds[g + 1] != bounds[g]) {
                    present |= 1 << g;
                }
            }
            flags |= GROUPED | present << GROUP_SHIFT;
            float[] planes = faces.planes();
            record[at + PLANES] = quantize(planes[0], 0) | quantize(planes[1], 1) << 8 | quantize(planes[2], 2) << 16 | quantize(planes[3], 3) << 24;
            record[at + PLANES + 1] = quantize(planes[4], 4) | quantize(planes[5], 5) << 8;
            record[at + OFFSETS] = bounds[1] | bounds[2] << 16;
            record[at + OFFSETS + 1] = bounds[3] | bounds[4] << 16;
            record[at + OFFSETS + 2] = bounds[5] | bounds[6] << 16;
        }
        record[at + FLAGS] = flags;
        if (quads > 0) {
            mask[id] |= 1 << layer;
        } else {
            mask[id] &= ~(1 << layer);
        }
        if (shown && ((before & 1 << layer) != 0 || quads > 0) && !queue(id, before)) {
            version++;
            if ((before == 0) != (mask[id] == 0)) {
                membership++;
            }
        }
        Origins.mask(id, mask[id]);
    }

    private static int[] appendRound = new int[4096];
    private static int[] queuedMask = new int[4096];
    private static int round = 1;
    private static int[] pending = new int[64];
    private static int pendingCount;
    static int[] removeMarks = new int[4096];
    static int removeStamp;
    static int[] buildIds = new int[64];
    static int buildCount;
    static final boolean SPLICE_TRANSLUCENT = Boolean.parseBoolean(System.getProperty("nimbus.splice", "false"));
    static int[] spliceIds = new int[16];
    static int spliceCount;

    // A listed section whose geometry changed is patched into the draw lists (old draws cut out, new ones appended)
    // instead of rebuilding every list; false when the engine cannot patch right now.
    private static boolean queue(int id, int before) {
        if (!Visibility.valid || !Terrain.active()) {
            return false;
        }
        if (appendRound[id] != round) {
            appendRound[id] = round;
            queuedMask[id] = before;
            if (pendingCount == pending.length) {
                pending = Arrays.copyOf(pending, pendingCount * 2);
            }
            pending[pendingCount++] = id;
        }
        return true;
    }

    // Visibility rebuilt its lists from the masks, which already include every queued section.
    static void clearPending() {
        pendingCount = 0;
        round++;
    }

    // Prepares the patch: marks old draws to cut, appends new sections to Visibility, lists what to (re)add.
    // Returns how many sections were queued, or -1 when one of them needs a full rebuild (translucent order).
    static int flushPending(int translucentLayer) {
        int count = pendingCount;
        if (count == 0) {
            return 0;
        }
        if (++removeStamp == 0) {
            Arrays.fill(removeMarks, 0);
            removeStamp = 1;
        }
        buildCount = 0;
        spliceCount = 0;
        boolean full = false;
        for (int i = 0; i < count; i++) {
            int id = pending[i];
            if (!listed(id)) {
                continue;
            }
            int was = queuedMask[id];
            int now = mask[id];
            if (translucentLayer >= 0 && ((was | now) >>> translucentLayer & 1) != 0) {
                if (!SPLICE_TRANSLUCENT || (was >>> translucentLayer & 1) == 0) {
                    // no place in the back-to-front list yet
                    full = true;
                } else {
                    if (spliceCount == spliceIds.length) {
                        spliceIds = Arrays.copyOf(spliceIds, spliceCount * 2);
                    }
                    spliceIds[spliceCount++] = id;
                }
            }
            if (was != 0) {
                removeMarks[id] = removeStamp;
            } else if (now != 0) {
                Visibility.append(id);
            }
            if (now != 0) {
                if (buildCount == buildIds.length) {
                    buildIds = Arrays.copyOf(buildIds, buildCount * 2);
                }
                buildIds[buildCount++] = id;
            }
        }
        clearPending();
        return full ? -1 : count;
    }

    static void origin(int id, int x, int y, int z) {
        if (id < 0) {
            return;
        }
        ensure(id);
        int at = id * ID_STRIDE;
        records[at] = x;
        records[at + 1] = y;
        records[at + 2] = z;
    }

    static void layers(int id, int bits) {
        if (id < 0) {
            return;
        }
        ensure(id);
        records[id * ID_STRIDE + 3] = bits;
    }

    // negative-facing groups store their highest plane (rounded up), positive-facing their lowest (rounded down)
    private static int quantize(float plane, int group) {
        float scaled = (plane + 16.0F) * 4.0F;
        int q = (group & 1) == 0 ? (int) Math.ceil(scaled) : (int) Math.floor(scaled);
        return Math.max(0, Math.min(255, q));
    }

    // Only sections in the current visible list affect the draw lists; the rest are picked up when the list changes.
    private static boolean listed(int id) {
        return !Visibility.valid || id >= visibleFrame.length || visibleFrame[id] == Visibility.stamp;
    }

    static void clear(int id, int layer) {
        if (id < 0 || id >= mask.length || layer < 0 || layer >= LAYERS) {
            return;
        }
        int key = id * LAYERS + layer;
        int at = id * ID_STRIDE + HEADER + layer * STRIDE;
        if ((mask[id] & 1 << layer) == 0 && records[at + COUNT] == 0) {
            // nothing of this layer is in any draw list
            return;
        }
        boolean shown = listed(id);
        int before = mask[id];
        boolean patched = shown && queue(id, before);
        if (shown && !patched) {
            version++;
        }
        records[at + COUNT] = 0;
        records[at + FLAGS] = 0;
        animated[key] = null;
        mask[id] &= ~(1 << layer);
        if (before != 0 && mask[id] == 0 && shown && !patched) {
            membership++;
        }
        Origins.mask(id, mask[id]);
    }
}

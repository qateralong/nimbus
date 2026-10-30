package com.qateralong.nimbus.render;

import java.util.Arrays;

public final class SectionData {
    static final int LAYERS = 8;
    static int[] mask = new int[4096];
    static boolean[] blockEntities = new boolean[4096];
    static final int START = 0;
    static final int COUNT = 1;
    static final int FLAGS = 2;
    static final int OFFSETS = 3;
    static final int PLANES = OFFSETS + Faces.GROUPS + 1;
    static final int STRIDE = PLANES + 6;
    static final int GROUPED = 1;
    static final int ANIMATED = 2;
    static final int GROUP_SHIFT = 8;
    static int[] records = new int[4096 * LAYERS * STRIDE];
    public static boolean[] dirty = new boolean[4096];
    public static int[] order = new int[4096];
    public static net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection[] sections = new net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection[4096];
    public static int[] dirtyIds = new int[1024];
    public static int dirtyListed;
    public static int dirtyCount;
    static int[][] animated = new int[4096 * LAYERS][];
    public static int version;
    public static volatile int membership;

    private SectionData() {
    }

    static void reserve(int id) {
        ensure(id);
    }

    public static void register(int id, net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section) {
        ensure(id);
        sections[id] = section;
    }

    public static void setDirty(int id, boolean value) {
        if (id < 0 || id >= dirty.length || dirty[id] == value) {
            return;
        }
        dirty[id] = value;
        if (!value) {
            dirtyCount--;
            return;
        }
        dirtyCount++;
        if (dirtyListed == dirtyIds.length) {
            compactDirty();
            if (dirtyListed * 2 > dirtyIds.length) {
                dirtyIds = Arrays.copyOf(dirtyIds, dirtyIds.length * 2);
            }
        }
        dirtyIds[dirtyListed++] = id;
    }

    public static void compactDirty() {
        int kept = 0;
        for (int i = 0; i < dirtyListed; i++) {
            int id = dirtyIds[i];
            if (dirty[id]) {
                dirtyIds[kept++] = id;
                dirty[id] = false;
            }
        }
        for (int i = 0; i < kept; i++) {
            dirty[dirtyIds[i]] = true;
        }
        dirtyListed = kept;
    }

    public static void setBlockEntities(int id, boolean present) {
        if (id >= 0 && id < blockEntities.length && blockEntities[id] != present) {
            blockEntities[id] = present;
            membership++;
        }
    }

    private static void ensure(int id) {
        if (id < mask.length) {
            return;
        }
        int size = Math.max(mask.length * 2, id + 1);
        mask = Arrays.copyOf(mask, size);
        blockEntities = Arrays.copyOf(blockEntities, size);
        records = Arrays.copyOf(records, size * LAYERS * STRIDE);
        dirty = Arrays.copyOf(dirty, size);
        order = Arrays.copyOf(order, size);
        sections = Arrays.copyOf(sections, size);
        animated = Arrays.copyOf(animated, size * LAYERS);
    }

    static void set(int id, int layer, int first, int quads, Faces faces, int[] sprites) {
        if (id < 0 || layer < 0 || layer >= LAYERS) {
            return;
        }
        ensure(id);
        version++;
        int key = id * LAYERS + layer;
        int at = key * STRIDE;
        int[] record = records;
        record[at + START] = first;
        record[at + COUNT] = quads;
        record[at + FLAGS] = (faces != null ? GROUPED : 0) | (sprites != null ? ANIMATED : 0);
        animated[key] = sprites;
        if (faces != null) {
            int[] bounds = faces.offsets();
            int present = 0;
            for (int g = 0; g < Faces.GROUPS; g++) {
                if (bounds[g + 1] != bounds[g]) {
                    present |= 1 << g;
                }
            }
            record[at + FLAGS] |= present << GROUP_SHIFT;
            System.arraycopy(bounds, 0, record, at + OFFSETS, Faces.GROUPS + 1);
            float[] planes = faces.planes();
            for (int i = 0; i < 6; i++) {
                record[at + PLANES + i] = Float.floatToRawIntBits(planes[i]);
            }
        }
        int before = mask[id];
        if (quads > 0) {
            mask[id] |= 1 << layer;
        } else {
            mask[id] &= ~(1 << layer);
        }
        if ((before == 0) != (mask[id] == 0)) {
            membership++;
        }
        Origins.mask(id, mask[id]);
    }

    static void clear(int id, int layer) {
        if (id < 0 || id >= mask.length || layer < 0 || layer >= LAYERS) {
            return;
        }
        version++;
        int key = id * LAYERS + layer;
        int at = key * STRIDE;
        records[at + COUNT] = 0;
        records[at + FLAGS] = 0;
        animated[key] = null;
        int before = mask[id];
        mask[id] &= ~(1 << layer);
        if (before != 0 && mask[id] == 0) {
            membership++;
        }
        Origins.mask(id, mask[id]);
    }
}

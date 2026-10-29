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
    static int[] records = new int[4096 * LAYERS * STRIDE];
    public static boolean[] dirty = new boolean[4096];
    static int[][] animated = new int[4096 * LAYERS][];

    private SectionData() {
    }

    static void reserve(int id) {
        ensure(id);
    }

    public static void setDirty(int id, boolean value) {
        if (id >= 0 && id < dirty.length) {
            dirty[id] = value;
        }
    }

    public static void setBlockEntities(int id, boolean present) {
        if (id >= 0 && id < blockEntities.length) {
            blockEntities[id] = present;
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
        animated = Arrays.copyOf(animated, size * LAYERS);
    }

    static void set(int id, int layer, int first, int quads, Faces faces, int[] sprites) {
        if (id < 0 || layer < 0 || layer >= LAYERS) {
            return;
        }
        ensure(id);
        int key = id * LAYERS + layer;
        int at = key * STRIDE;
        int[] record = records;
        record[at + START] = first;
        record[at + COUNT] = quads;
        record[at + FLAGS] = (faces != null ? GROUPED : 0) | (sprites != null ? ANIMATED : 0);
        animated[key] = sprites;
        if (faces != null) {
            System.arraycopy(faces.offsets(), 0, record, at + OFFSETS, Faces.GROUPS + 1);
            float[] bounds = faces.planes();
            for (int i = 0; i < 6; i++) {
                record[at + PLANES + i] = Float.floatToRawIntBits(bounds[i]);
            }
        }
        if (quads > 0) {
            mask[id] |= 1 << layer;
        } else {
            mask[id] &= ~(1 << layer);
        }
        Origins.mask(id, mask[id]);
    }

    static void clear(int id, int layer) {
        if (id < 0 || id >= mask.length || layer < 0 || layer >= LAYERS) {
            return;
        }
        int key = id * LAYERS + layer;
        int at = key * STRIDE;
        records[at + COUNT] = 0;
        records[at + FLAGS] = 0;
        animated[key] = null;
        mask[id] &= ~(1 << layer);
        Origins.mask(id, mask[id]);
    }
}

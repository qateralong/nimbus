package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.qateralong.nimbus.Nimbus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryUtil;

public final class AnimatedSprites {
    private static final int CELLS = 64;
    private static final List<TextureAtlasSprite> SPRITES = new ArrayList<>();
    private static volatile Grid grid;
    private static int[] active = new int[0];
    public static volatile boolean enabled;

    private record Grid(float[] rects, int[][] cells) {
    }

    private AnimatedSprites() {
    }

    public static void reset() {
        SPRITES.clear();
        grid = null;
    }

    public static int register(TextureAtlasSprite sprite) {
        SPRITES.add(sprite);
        return SPRITES.size() - 1;
    }

    public static void built() {
        int count = SPRITES.size();
        float[] rects = new float[count * 4];
        List<List<Integer>> lists = new ArrayList<>();
        for (int i = 0; i < CELLS * CELLS; i++) {
            lists.add(null);
        }
        for (int i = 0; i < count; i++) {
            TextureAtlasSprite sprite = SPRITES.get(i);
            rects[i * 4] = sprite.getU0();
            rects[i * 4 + 1] = sprite.getV0();
            rects[i * 4 + 2] = sprite.getU1();
            rects[i * 4 + 3] = sprite.getV1();
            int x0 = cell(sprite.getU0());
            int x1 = cell(sprite.getU1());
            int y0 = cell(sprite.getV0());
            int y1 = cell(sprite.getV1());
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int key = y * CELLS + x;
                    if (lists.get(key) == null) {
                        lists.set(key, new ArrayList<>());
                    }
                    lists.get(key).add(i);
                }
            }
        }
        int[][] cells = new int[CELLS * CELLS][];
        for (int i = 0; i < cells.length; i++) {
            List<Integer> list = lists.get(i);
            cells[i] = list == null ? null : list.stream().mapToInt(Integer::intValue).toArray();
        }
        active = new int[count];
        grid = new Grid(rects, cells);
    }

    private static int cell(float value) {
        return Math.max(0, Math.min(CELLS - 1, (int) (value * CELLS)));
    }

    static int[] find(MeshData mesh, int quads) {
        Grid current = grid;
        if (current == null) {
            return null;
        }
        long source = MemoryUtil.memAddress(mesh.vertexBuffer());
        long[] bits = null;
        for (int quad = 0; quad < quads; quad++) {
            long base = source + (long) quad * 128;
            float u = 0.0F;
            float v = 0.0F;
            for (int corner = 0; corner < 4; corner++) {
                u += MemoryUtil.memGetFloat(base + corner * 32L + 16);
                v += MemoryUtil.memGetFloat(base + corner * 32L + 20);
            }
            u *= 0.25F;
            v *= 0.25F;
            int[] candidates = current.cells[cell(v) * CELLS + cell(u)];
            if (candidates == null) {
                continue;
            }
            for (int index : candidates) {
                float[] r = current.rects;
                if (u >= r[index * 4] && u <= r[index * 4 + 2] && v >= r[index * 4 + 1] && v <= r[index * 4 + 3]) {
                    if (bits == null) {
                        bits = new long[(current.rects.length / 4 + 63) / 64];
                    }
                    bits[index >>> 6] |= 1L << (index & 63);
                    break;
                }
            }
        }
        return indices(bits);
    }

    static int lookup(float u, float v) {
        Grid current = grid;
        if (current == null) {
            return -1;
        }
        int[] candidates = current.cells[cell(v) * CELLS + cell(u)];
        if (candidates == null) {
            return -1;
        }
        float[] r = current.rects;
        for (int index : candidates) {
            if (u >= r[index * 4] && u <= r[index * 4 + 2] && v >= r[index * 4 + 1] && v <= r[index * 4 + 3]) {
                return index;
            }
        }
        return -1;
    }

    private record SpriteSet(long[] bits) {
        @Override
        public boolean equals(Object other) {
            return other instanceof SpriteSet set && java.util.Arrays.equals(this.bits, set.bits);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(this.bits);
        }
    }

    // sections share a handful of distinct sets (water, lava, fire...), so equal sets become one array
    private static final java.util.concurrent.ConcurrentHashMap<SpriteSet, int[]> SETS = new java.util.concurrent.ConcurrentHashMap<>();

    static int[] indices(long[] bits) {
        if (bits == null) {
            return null;
        }
        int last = bits.length;
        while (last > 0 && bits[last - 1] == 0L) {
            last--;
        }
        long[] key = java.util.Arrays.copyOf(bits, last);
        if (SETS.size() > 4096) {
            SETS.clear();
        }
        return SETS.computeIfAbsent(new SpriteSet(key), set -> unpack(set.bits()));
    }

    private static int[] unpack(long[] bits) {
        int total = 0;
        for (long word : bits) {
            total += Long.bitCount(word);
        }
        int[] result = new int[total];
        int at = 0;
        for (int word = 0; word < bits.length; word++) {
            long value = bits[word];
            while (value != 0) {
                result[at++] = word * 64 + Long.numberOfTrailingZeros(value);
                value &= value - 1;
            }
        }
        return result;
    }

    private record IndexSet(int[] indices) {
        @Override
        public boolean equals(Object other) {
            return other instanceof IndexSet set && java.util.Arrays.equals(this.indices, set.indices);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(this.indices);
        }
    }

    static final int NO_SET = 0xFFFF;
    private static final java.util.HashMap<IndexSet, Integer> SET_IDS = new java.util.HashMap<>();
    private static int[][] setsById = new int[64][];

    // Main thread: a small number per distinct sprite set, so draw-list building can dedupe with one compare.
    static int setId(int[] indices) {
        Integer known = SET_IDS.get(new IndexSet(indices));
        if (known != null) {
            return known;
        }
        int id = SET_IDS.size();
        if (id >= NO_SET) {
            return NO_SET;
        }
        if (id == setsById.length) {
            setsById = java.util.Arrays.copyOf(setsById, id * 2);
        }
        setsById[id] = indices;
        SET_IDS.put(new IndexSet(indices), id);
        return id;
    }

    static int[] set(int id) {
        return setsById[id];
    }

    static int setCount() {
        return SET_IDS.size();
    }

    static void markBits(long[] bits) {
        int[] states = active;
        int tick = Nimbus.ticks;
        for (int word = 0; word < bits.length; word++) {
            long value = bits[word];
            while (value != 0) {
                int index = word * 64 + Long.numberOfTrailingZeros(value);
                value &= value - 1;
                if (index < states.length) {
                    states[index] = tick;
                }
            }
        }
    }

    static void mark(int[] indices) {
        int[] states = active;
        int tick = Nimbus.ticks;
        for (int index : indices) {
            if (index < states.length) {
                states[index] = tick;
            }
        }
    }

    public static void mark(TextureAtlasSprite sprite) {
        if (!enabled || sprite == null) {
            return;
        }
        int index = ((AnimatedIndex) sprite).nimbus$animIndex();
        int[] states = active;
        if (index >= 0 && index < states.length) {
            states[index] = Nimbus.ticks;
        }
    }

    public static void mark(List<BakedQuad> quads) {
        if (!enabled) {
            return;
        }
        for (int i = 0, size = quads.size(); i < size; i++) {
            mark(quads.get(i).getSprite());
        }
    }

    public static boolean skip(int index) {
        int[] states = active;
        return enabled && index >= 0 && index < states.length && states[index] < Nimbus.ticks - 1;
    }
}

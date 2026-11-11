package com.qateralong.nimbus.render;

import com.qateralong.nimbus.Nimbus;

// Last events of every section's stored layers, for bug reports: which upload path a layer took, whether its store
// landed, what released it. Racy by design; it only has to be readable after the fact.
public final class SectionEvents {
    public static final int UPLOAD_PACKED = 1;
    public static final int UPLOAD_PACKER = 2;
    public static final int UPLOAD_VANILLA = 3;
    public static final int STORED = 4;
    public static final int DROPPED_CANCELLED = 5;
    public static final int DROPPED_INVALID = 6;
    public static final int DROPPED_FULL = 7;
    public static final int RELEASED_COMPILED = 8;
    public static final int RELEASED_CLOSE = 9;
    public static final int RESET = 10;
    public static final int COMPILED = 11;
    public static final int REPAIR = 12;
    public static final int UPLOAD_INACTIVE = 13;
    private static final String[] NAMES = {"?", "upload packed", "upload packer", "upload vanilla (not stored)", "stored", "dropped: task cancelled",
            "dropped: buffer closed", "dropped: storage full", "released: compiled without layer", "released: buffer closed", "reset", "compiled",
            "repair rebuild", "upload while engine inactive"};
    private static final int SLOTS = 8;
    private static long[] events = new long[0];
    private static byte[] heads = new byte[0];

    private SectionEvents() {
    }

    // value: quads, or the layer mask for COMPILED
    public static void add(int section, int type, int layer, int value) {
        if (section < 0) {
            return;
        }
        long[] all = events;
        if (section >= heads.length) {
            grow(section);
            all = events;
        }
        byte[] head = heads;
        int at = head[section] & 0xFF;
        head[section] = (byte) ((at + 1) % SLOTS);
        all[section * SLOTS + at] = (long) (Nimbus.frames & 0xFFFFFF) << 40 | (long) (type & 0xFF) << 32 | (long) (layer & 0xFF) << 24 | value & 0xFFFFFFL;
    }

    private static synchronized void grow(int section) {
        if (section < heads.length) {
            return;
        }
        int size = Math.max(section + 1, heads.length * 2 + 1024);
        long[] all = java.util.Arrays.copyOf(events, size * SLOTS);
        byte[] head = java.util.Arrays.copyOf(heads, size);
        events = all;
        heads = head;
    }

    static String history(int section, java.util.List<net.minecraft.client.renderer.RenderType> layers) {
        if (section < 0 || section >= heads.length) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int start = heads[section] & 0xFF;
        int now = Nimbus.frames & 0xFFFFFF;
        for (int i = 0; i < SLOTS; i++) {
            long e = events[section * SLOTS + (start + i) % SLOTS];
            if (e == 0L) {
                continue;
            }
            int frame = (int) (e >>> 40) & 0xFFFFFF;
            int type = (int) (e >>> 32) & 0xFF;
            int layer = (int) (e >>> 24) & 0xFF;
            int value = (int) (e & 0xFFFFFF);
            out.append("\n      -").append((now - frame) & 0xFFFFFF).append("f ").append(type < NAMES.length ? NAMES[type] : "?");
            if (type == COMPILED) {
                out.append(" layers");
                for (int l = 0; l < layers.size(); l++) {
                    if ((value >>> l & 1) != 0) {
                        out.append(' ').append(Diagnostics.name(layers.get(l)));
                    }
                }
            } else if (type != RESET && type != REPAIR) {
                out.append(' ').append(layer < layers.size() ? Diagnostics.name(layers.get(layer)) : "layer " + layer).append(' ').append(value);
            }
        }
        return out.toString();
    }
}

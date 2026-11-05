package com.qateralong.nimbus.light;

// Light of one chunk column, one nibble array per light section (null = not stored). Published arrays are never
// written; the engine edits copies in the work arrays and swaps them in after each batch.
public final class LightColumn {
    final byte[][] sky;
    final byte[][] block;
    byte[][] skyWork;
    byte[][] blockWork;
    boolean touched;
    volatile boolean on;

    LightColumn(int sections) {
        this.sky = new byte[sections][];
        this.block = new byte[sections][];
    }
}

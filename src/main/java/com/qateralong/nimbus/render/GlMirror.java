package com.qateralong.nimbus.render;

// What GlStateManager last set for blending and depth, so state can be saved without querying GL.
public final class GlMirror {
    public static boolean blend;
    public static int srcRgb = 1;
    public static int dstRgb = 0;
    public static int srcAlpha = 1;
    public static int dstAlpha = 0;
    public static boolean depth;
    public static int depthFunc = 0x201;
    public static boolean depthMask = true;
    // while drawing into a target that is composited later, "over" blending must accumulate alpha too
    public static boolean overAlpha;

    private GlMirror() {
    }
}

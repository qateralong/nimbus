package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.caffeinemc.mods.sodium.client.util.NativeImageHelper;
import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
import org.lwjgl.system.MemoryUtil;

public final class MipFill {
    private static final float[] LINEAR = new float[256];
    private static final ThreadLocal<int[]> SCRATCH = ThreadLocal.withInitial(() -> new int[4096]);

    static {
        for (int i = 0; i < 256; i++) {
            LINEAR[i] = ColorSRGB.srgbToLinear(i);
        }
    }

    private MipFill() {
    }

    public static void fill(NativeImage image) {
        long pointer = NativeImageHelper.getPointerRGBA(image);
        int count = image.getWidth() * image.getHeight();
        int[] pixels = SCRATCH.get();
        if (pixels.length < count) {
            pixels = new int[count];
            if (count <= 1 << 16) {
                SCRATCH.set(pixels);
            }
        }
        MemoryUtil.memIntBuffer(pointer, count).get(pixels, 0, count);
        boolean transparent = false;
        for (int i = 0; i < count; i++) {
            if (pixels[i] >>> 24 == 0) {
                transparent = true;
                break;
            }
        }
        if (!transparent) {
            return;
        }
        float[] linear = LINEAR;
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        float total = 0.0F;
        for (int i = 0; i < count; i++) {
            int color = pixels[i];
            int alpha = color >>> 24;
            if (alpha != 0) {
                float weight = alpha;
                r += linear[color & 0xFF] * weight;
                g += linear[color >>> 8 & 0xFF] * weight;
                b += linear[color >>> 16 & 0xFF] * weight;
                total += weight;
            }
        }
        if (total == 0.0F) {
            return;
        }
        int average = ColorSRGB.linearToSrgb(r / total, g / total, b / total, 0);
        for (int i = 0; i < count; i++) {
            if (pixels[i] >>> 24 == 0) {
                pixels[i] = average;
            }
        }
        MemoryUtil.memIntBuffer(pointer, count).put(pixels, 0, count);
    }
}

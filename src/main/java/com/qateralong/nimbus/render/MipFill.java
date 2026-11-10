package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.NativeImage;

// Transparent texels of a sprite get the alpha-weighted average colour (in linear space) of its visible ones while mipmaps
// are built, so smaller levels stop pulling in black; the full-size level keeps its original texels.
public final class MipFill {
    private static final float[] LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            float c = i / 255.0F;
            LINEAR[i] = c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4F);
        }
    }

    private MipFill() {
    }

    private static int srgb(float linear) {
        float c = linear <= 0.0031308F ? linear * 12.92F : 1.055F * (float) Math.pow(linear, 1.0F / 2.4F) - 0.055F;
        return Math.max(0, Math.min(255, Math.round(c * 255.0F)));
    }

    // returns the overwritten texels as (x, y, colour) triples so the base level can be restored after mipmapping
    public static int[] fill(NativeImage image) {
        if (image == null || image.format() != NativeImage.Format.RGBA) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        float total = 0.0F;
        int transparent = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // ABGR
                int color = image.getPixelRGBA(x, y);
                int alpha = color >>> 24;
                if (alpha == 0) {
                    transparent++;
                    continue;
                }
                r += LINEAR[color & 0xFF] * alpha;
                g += LINEAR[color >>> 8 & 0xFF] * alpha;
                b += LINEAR[color >>> 16 & 0xFF] * alpha;
                total += alpha;
            }
        }
        if (transparent == 0 || total == 0.0F) {
            return null;
        }
        // darkened: distant solid-rendered cutout (fast leaves) keeps its contrast
        float k = 0.2F / total;
        int average = srgb(r * k) | srgb(g * k) << 8 | srgb(b * k) << 16;
        int[] saved = new int[transparent * 3];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getPixelRGBA(x, y);
                if (color >>> 24 == 0) {
                    saved[i++] = x;
                    saved[i++] = y;
                    saved[i++] = color;
                    image.setPixelRGBA(x, y, average);
                }
            }
        }
        return saved;
    }

    public static void restore(NativeImage image, int[] saved) {
        for (int i = 0; i < saved.length; i += 3) {
            image.setPixelRGBA(saved[i], saved[i + 1], saved[i + 2]);
        }
    }
}

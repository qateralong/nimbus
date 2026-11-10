package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.NativeImage;

// Transparent texels of a sprite get the alpha-weighted average colour (in linear space) of its visible ones, alpha kept 0:
// mipmaps stop pulling in black, solid-rendered cutout blocks (fast leaves) show colour instead of black.
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

    public static void fill(NativeImage image) {
        if (image == null || image.format() != NativeImage.Format.RGBA) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        float total = 0.0F;
        boolean transparent = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // ABGR
                int color = image.getPixelRGBA(x, y);
                int alpha = color >>> 24;
                if (alpha == 0) {
                    transparent = true;
                    continue;
                }
                r += LINEAR[color & 0xFF] * alpha;
                g += LINEAR[color >>> 8 & 0xFF] * alpha;
                b += LINEAR[color >>> 16 & 0xFF] * alpha;
                total += alpha;
            }
        }
        if (!transparent || total == 0.0F) {
            return;
        }
        // darkened: solid-rendered cutout (fast leaves) keeps its texture's contrast, as with Sodium
        float k = 0.2F / total;
        int average = srgb(r * k) | srgb(g * k) << 8 | srgb(b * k) << 16;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (image.getPixelRGBA(x, y) >>> 24 == 0) {
                    image.setPixelRGBA(x, y, average);
                }
            }
        }
    }
}

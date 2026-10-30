package com.qateralong.nimbus;

import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SkySampler {
    private static final double[] KERNEL = {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};
    private static final double[] COLORS = new double[216 * 3];
    private static Object owner;
    private static int baseX;
    private static int baseY;
    private static int baseZ;
    private static int filledTick = Integer.MIN_VALUE;

    private SkySampler() {
    }

    public static Vec3 sample(Object level, Vec3 pos, CubicSampler.Vec3Fetcher fetcher) {
        int x = Mth.floor(pos.x());
        int y = Mth.floor(pos.y());
        int z = Mth.floor(pos.z());
        int tick = Nimbus.ticks;
        if (level != owner || x != baseX || y != baseY || z != baseZ || tick - filledTick >= 20 || tick < filledTick) {
            int at = 0;
            for (int a = 0; a < 6; a++) {
                for (int b = 0; b < 6; b++) {
                    for (int c = 0; c < 6; c++) {
                        Vec3 color = fetcher.fetch(x - 2 + a, y - 2 + b, z - 2 + c);
                        COLORS[at++] = color.x;
                        COLORS[at++] = color.y;
                        COLORS[at++] = color.z;
                    }
                }
            }
            owner = level;
            baseX = x;
            baseY = y;
            baseZ = z;
            filledTick = tick;
        }
        double dx = pos.x() - x;
        double dy = pos.y() - y;
        double dz = pos.z() - z;
        double total = 0.0;
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        int at = 0;
        for (int a = 0; a < 6; a++) {
            double wa = Mth.lerp(dx, KERNEL[a + 1], KERNEL[a]);
            for (int b = 0; b < 6; b++) {
                double wab = wa * Mth.lerp(dy, KERNEL[b + 1], KERNEL[b]);
                for (int c = 0; c < 6; c++) {
                    double w = wab * Mth.lerp(dz, KERNEL[c + 1], KERNEL[c]);
                    total += w;
                    r += COLORS[at++] * w;
                    g += COLORS[at++] * w;
                    bl += COLORS[at++] * w;
                }
            }
        }
        double scale = 1.0 / total;
        return new Vec3(r * scale, g * scale, bl * scale);
    }
}

package com.qateralong.nimbus.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

// Biome tint for one section compile window: biomes are sampled once per layer over the window plus the blend
// radius, and the (2r+1)^2 average is a separable box sum. Same integer math as ClientLevel.calculateBlockTint.
public final class BiomeBlend {
    public static final int SLOTS = 3;
    private final Level level;
    private final int size;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int radius;
    private final int side;
    private final Biome[][] biomes;
    private final ColorResolver[] resolvers = new ColorResolver[SLOTS];
    private final int[][][] colors;
    private int[] raw = new int[0];
    private int[] rowR = new int[0];
    private int[] rowG = new int[0];
    private int[] rowB = new int[0];

    public BiomeBlend(Level level, int originX, int originY, int originZ, int size) {
        this.level = level;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.size = size;
        this.radius = Math.max(0, Math.min(15, Minecraft.getInstance().options.biomeBlendRadius().get()));
        this.side = size + 2 * this.radius;
        this.biomes = new Biome[size][];
        this.colors = new int[SLOTS][size][];
    }

    // Local window coordinates; returns -1 when this resolver has no free slot.
    public int color(ColorResolver resolver, int x, int y, int z) {
        int slot = 0;
        while (slot < SLOTS && this.resolvers[slot] != null && this.resolvers[slot] != resolver) {
            slot++;
        }
        if (slot == SLOTS) {
            return -1;
        }
        this.resolvers[slot] = resolver;
        int[] layer = this.colors[slot][y];
        if (layer == null) {
            layer = this.blend(resolver, y);
            this.colors[slot][y] = layer;
        }
        return layer[z * this.size + x];
    }

    private Biome[] layer(int y) {
        Biome[] layer = this.biomes[y];
        if (layer != null) {
            return layer;
        }
        int side = this.side;
        layer = new Biome[side * side];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int wy = this.originY + y;
        int baseX = this.originX - this.radius;
        int baseZ = this.originZ - this.radius;
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                layer[dz * side + dx] = this.level.getBiome(pos.set(baseX + dx, wy, baseZ + dz)).value();
            }
        }
        this.biomes[y] = layer;
        return layer;
    }

    private int[] blend(ColorResolver resolver, int y) {
        Biome[] layer = this.layer(y);
        int size = this.size;
        int side = this.side;
        int r = this.radius;
        int baseX = this.originX - r;
        int baseZ = this.originZ - r;
        int[] out = new int[size * size];
        if (r == 0) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    out[z * size + x] = resolver.getColor(layer[z * side + x], baseX + x, baseZ + z);
                }
            }
            return out;
        }
        int cells = side * side;
        if (this.raw.length < cells) {
            this.raw = new int[cells];
        }
        int rows = side * size;
        if (this.rowR.length < rows) {
            this.rowR = new int[rows];
            this.rowG = new int[rows];
            this.rowB = new int[rows];
        }
        int[] raw = this.raw;
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                int i = dz * side + dx;
                raw[i] = resolver.getColor(layer[i], baseX + dx, baseZ + dz);
            }
        }
        int width = 2 * r + 1;
        int[] rowR = this.rowR;
        int[] rowG = this.rowG;
        int[] rowB = this.rowB;
        for (int dz = 0; dz < side; dz++) {
            int row = dz * side;
            int sr = 0;
            int sg = 0;
            int sb = 0;
            for (int k = 0; k < width; k++) {
                int c = raw[row + k];
                sr += c >> 16 & 255;
                sg += c >> 8 & 255;
                sb += c & 255;
            }
            for (int x = 0; ; x++) {
                int at = dz * size + x;
                rowR[at] = sr;
                rowG[at] = sg;
                rowB[at] = sb;
                if (x == size - 1) {
                    break;
                }
                int leave = raw[row + x];
                int enter = raw[row + x + width];
                sr += (enter >> 16 & 255) - (leave >> 16 & 255);
                sg += (enter >> 8 & 255) - (leave >> 8 & 255);
                sb += (enter & 255) - (leave & 255);
            }
        }
        int count = width * width;
        for (int x = 0; x < size; x++) {
            int sr = 0;
            int sg = 0;
            int sb = 0;
            for (int k = 0; k < width; k++) {
                int at = k * size + x;
                sr += rowR[at];
                sg += rowG[at];
                sb += rowB[at];
            }
            for (int z = 0; ; z++) {
                out[z * size + x] = (sr / count & 255) << 16 | (sg / count & 255) << 8 | sb / count & 255;
                if (z == size - 1) {
                    break;
                }
                int leave = z * size + x;
                int enter = (z + width) * size + x;
                sr += rowR[enter] - rowR[leave];
                sg += rowG[enter] - rowG[leave];
                sb += rowB[enter] - rowB[leave];
            }
        }
        return out;
    }
}

package com.qateralong.nimbus.lod;

import com.mojang.blaze3d.platform.NativeImage;
import com.qateralong.nimbus.mixin.SpriteImageAccessor;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

// Distant colour of a block seen from above: the average texel of its top face, times its tint where it has one.
public final class LodColors {
    private static final long UNKNOWN = Long.MIN_VALUE;
    private static final Reference2LongOpenHashMap<BlockState> BASE = new Reference2LongOpenHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    static {
        BASE.defaultReturnValue(UNKNOWN);
    }

    private LodColors() {
    }

    public static void clear() {
        BASE.clear();
        waterBase = 0;
    }

    // ARGB with the tint applied; alpha 0 means nothing visible from above
    static int color(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
            if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                int tint = net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, pos);
                return 0xFF000000 | multiply(waterBase(), tint);
            }
            long base = BASE.getLong(state);
            if (base == UNKNOWN) {
                base = average(Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(state)) & 0xFFFFFFFFL | -1L << 32;
                BASE.put(state, base);
            }
            return (int) base;
        }
        long base = BASE.getLong(state);
        if (base == UNKNOWN) {
            base = base(state);
            BASE.put(state, base);
        }
        int argb = (int) base;
        int tintIndex = (int) (base >> 32);
        if (tintIndex >= 0) {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, tintIndex);
            if (tint != -1) {
                argb = argb & 0xFF000000 | multiply(argb, tint);
            }
        }
        return argb;
    }

    private static int waterBase;

    private static int waterBase() {
        if (waterBase == 0) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockModelShaper()
                    .getParticleIcon(net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            waterBase = average(sprite) | 0xFF000000;
        }
        return waterBase;
    }

    private static long base(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        BakedQuad quad = first(model.getQuads(state, Direction.UP, RANDOM));
        if (quad == null) {
            quad = first(model.getQuads(state, null, RANDOM));
        }
        TextureAtlasSprite sprite = quad != null ? quad.getSprite() : model.getParticleIcon();
        int argb = average(sprite);
        int tint = quad != null && quad.isTinted() ? quad.getTintIndex() : -1;
        return (long) tint << 32 | argb & 0xFFFFFFFFL;
    }

    private static BakedQuad first(List<BakedQuad> quads) {
        return quads.isEmpty() ? null : quads.get(0);
    }

    private static int average(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        NativeImage image = ((SpriteImageAccessor) sprite.contents()).nimbus$image();
        if (image == null) {
            return 0;
        }
        long r = 0;
        long g = 0;
        long b = 0;
        long n = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, Math.min(width, height) / 16);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int abgr = image.getPixelRGBA(x, y);
                int a = abgr >>> 24;
                if (a < 32) {
                    continue;
                }
                r += abgr & 255;
                g += abgr >> 8 & 255;
                b += abgr >> 16 & 255;
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static int multiply(int argb, int tint) {
        int r = (argb >> 16 & 255) * (tint >> 16 & 255) / 255;
        int g = (argb >> 8 & 255) * (tint >> 8 & 255) / 255;
        int b = (argb & 255) * (tint & 255) / 255;
        return r << 16 | g << 8 | b;
    }
}

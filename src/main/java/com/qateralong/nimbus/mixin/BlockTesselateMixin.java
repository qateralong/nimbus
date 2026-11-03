package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.AnimatedSprites;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Same work as vanilla, but the per-block and per-quad scratch objects come from the calling thread instead of the heap.
@Mixin(ModelBlockRenderer.class)
public abstract class BlockTesselateMixin {
    @Unique
    private static final Direction[] NIMBUS_DIRECTIONS = Direction.values();

    @Unique
    private static final ThreadLocal<Scratch> NIMBUS_SCRATCH = ThreadLocal.withInitial(Scratch::new);

    @Shadow
    @Final
    private BlockColors blockColors;

    @Shadow
    protected abstract void renderModelFaceAO(BlockAndTintGetter level, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                              List<BakedQuad> quads, float[] shape, BitSet flags, ModelBlockRenderer.AmbientOcclusionFace face, int overlay);

    @Shadow
    protected abstract void renderModelFaceFlat(BlockAndTintGetter level, BlockState state, BlockPos pos, int light, int overlay, boolean repackLight,
                                                PoseStack poseStack, VertexConsumer consumer, List<BakedQuad> quads, BitSet flags);

    @Unique
    private static final class Scratch {
        final float[] shape = new float[NIMBUS_DIRECTIONS.length * 2];
        final BitSet flags = new BitSet(3);
        final ModelBlockRenderer.AmbientOcclusionFace face = new ModelBlockRenderer.AmbientOcclusionFace();
        final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        final float[] brightness = new float[4];
        final int[] lightmap = new int[4];
    }

    /**
     * @author qateralong
     * @reason vanilla allocates the shape array, flags, AO face and a mutable position for every block
     */
    @Overwrite
    public void tesselateWithAO(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                boolean checkSides, RandomSource random, long seed, int overlay) {
        Scratch scratch = NIMBUS_SCRATCH.get();
        float[] shape = scratch.shape;
        BitSet flags = scratch.flags;
        ModelBlockRenderer.AmbientOcclusionFace face = scratch.face;
        BlockPos.MutableBlockPos neighbour = scratch.neighbour;
        for (Direction direction : NIMBUS_DIRECTIONS) {
            random.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, direction, random);
            if (quads.isEmpty()) {
                continue;
            }
            neighbour.setWithOffset(pos, direction);
            if (checkSides && !Block.shouldRenderFace(state, level, pos, direction, neighbour)) {
                continue;
            }
            this.renderModelFaceAO(level, state, pos, poseStack, consumer, quads, shape, flags, face, overlay);
        }
        random.setSeed(seed);
        List<BakedQuad> quads = model.getQuads(state, null, random);
        if (!quads.isEmpty()) {
            this.renderModelFaceAO(level, state, pos, poseStack, consumer, quads, shape, flags, face, overlay);
        }
    }

    /**
     * @author qateralong
     * @reason vanilla allocates the flags and a mutable position for every block
     */
    @Overwrite
    public void tesselateWithoutAO(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                   boolean checkSides, RandomSource random, long seed, int overlay) {
        Scratch scratch = NIMBUS_SCRATCH.get();
        BitSet flags = scratch.flags;
        BlockPos.MutableBlockPos neighbour = scratch.neighbour;
        for (Direction direction : NIMBUS_DIRECTIONS) {
            random.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, direction, random);
            if (quads.isEmpty()) {
                continue;
            }
            neighbour.setWithOffset(pos, direction);
            if (checkSides && !Block.shouldRenderFace(state, level, pos, direction, neighbour)) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, state, neighbour);
            this.renderModelFaceFlat(level, state, pos, light, overlay, false, poseStack, consumer, quads, flags);
        }
        random.setSeed(seed);
        List<BakedQuad> quads = model.getQuads(state, null, random);
        if (!quads.isEmpty()) {
            this.renderModelFaceFlat(level, state, pos, -1, overlay, true, poseStack, consumer, quads, flags);
        }
    }

    /**
     * @author qateralong
     * @reason two small arrays per quad; also marks animated sprites seen on the render thread
     */
    @Overwrite
    private void putQuadData(BlockAndTintGetter level, BlockState state, BlockPos pos, VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                             float b0, float b1, float b2, float b3, int l0, int l1, int l2, int l3, int overlay) {
        if (AnimatedSprites.enabled && RenderSystem.isOnRenderThread()) {
            AnimatedSprites.mark(quad.getSprite());
        }
        float r;
        float g;
        float b;
        if (quad.isTinted()) {
            int color = this.blockColors.getColor(state, level, pos, quad.getTintIndex());
            r = (float) (color >> 16 & 255) / 255.0F;
            g = (float) (color >> 8 & 255) / 255.0F;
            b = (float) (color & 255) / 255.0F;
        } else {
            r = 1.0F;
            g = 1.0F;
            b = 1.0F;
        }
        Scratch scratch = NIMBUS_SCRATCH.get();
        float[] brightness = scratch.brightness;
        brightness[0] = b0;
        brightness[1] = b1;
        brightness[2] = b2;
        brightness[3] = b3;
        int[] lightmap = scratch.lightmap;
        lightmap[0] = l0;
        lightmap[1] = l1;
        lightmap[2] = l2;
        lightmap[3] = l3;
        consumer.putBulkData(pose, quad, brightness, r, g, b, 1.0F, lightmap, overlay, true);
    }
}

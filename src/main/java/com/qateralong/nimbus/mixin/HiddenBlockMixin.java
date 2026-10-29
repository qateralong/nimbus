package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class HiddenBlockMixin {
    @Unique
    private static final Direction[] DIRECTIONS = Direction.values();
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOUR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void nimbus$hidden(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose, VertexConsumer consumer,
                               boolean checkSides, RandomSource random, long seed, int overlay, CallbackInfo ci) {
        if (!checkSides || !state.isSolidRender(level, pos) || !model.isVanillaAdapter() || !NimbusConfig.get().enabled) {
            return;
        }
        BlockPos.MutableBlockPos neighbour = NEIGHBOUR.get();
        for (Direction direction : DIRECTIONS) {
            if (Block.shouldRenderFace(state, level, pos, direction, neighbour.setWithOffset(pos, direction))) {
                return;
            }
        }
        random.setSeed(seed);
        if (model.getQuads(state, null, random).isEmpty()) {
            ci.cancel();
        }
    }
}

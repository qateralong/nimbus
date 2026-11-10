package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.pack.BlockIds;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Under a shader pack, every block and fluid meshed into a section tags its vertices with the pack's block id.
@Mixin(SectionCompiler.class)
public abstract class PackSectionIdMixin {
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
    private void nimbus$packLiquid(BlockRenderDispatcher dispatcher, BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState state,
                                   FluidState fluid, Operation<Void> original) {
        if (!BlockIds.enabled()) {
            original.call(dispatcher, pos, level, consumer, state, fluid);
            return;
        }
        BlockIds.begin(fluid.createLegacyBlock());
        try {
            original.call(dispatcher, pos, level, consumer, state, fluid);
        } finally {
            BlockIds.end();
        }
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V"))
    private void nimbus$packBlock(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, VertexConsumer consumer,
                                  boolean cull, RandomSource random, Operation<Void> original) {
        if (!BlockIds.enabled()) {
            original.call(dispatcher, state, pos, level, pose, consumer, cull, random);
            return;
        }
        BlockIds.begin(state);
        try {
            original.call(dispatcher, state, pos, level, pose, consumer, cull, random);
        } finally {
            BlockIds.end();
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.Cull;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void nimbus$cull(E blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (com.qateralong.nimbus.Chests.meshed(blockEntity)
                && (!com.qateralong.nimbus.Chests.animated(blockEntity) || !com.qateralong.nimbus.Chests.drawEntity(blockEntity.getBlockPos()))) {
            ci.cancel();
            return;
        }
        com.qateralong.nimbus.NimbusConfig config = com.qateralong.nimbus.NimbusConfig.get();
        if (config.enabled && (!config.enchantingBook && blockEntity instanceof net.minecraft.world.level.block.entity.EnchantingTableBlockEntity
                || !config.pistonAnimation && blockEntity instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity)) {
            ci.cancel();
            return;
        }
        if (!Cull.blockEntityVisible(blockEntity)) {
            ci.cancel();
        }
    }
}

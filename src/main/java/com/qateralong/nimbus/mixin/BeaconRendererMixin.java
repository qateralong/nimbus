package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.Cull;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public abstract class BeaconRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void nimbus$beam(BeaconBlockEntity beacon, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled) {
            return;
        }
        if (!config.beaconBeams) {
            ci.cancel();
            return;
        }
        if (config.beaconCulling && Cull.frustum != null && beacon.getLevel() != null) {
            BlockPos pos = beacon.getBlockPos();
            AABB column = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + net.minecraft.client.renderer.blockentity.BeaconRenderer.MAX_RENDER_Y, pos.getZ() + 2);
            if (!Cull.frustum.isVisible(column)) {
                ci.cancel();
            }
        }
    }
}

package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.TheEndGatewayRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TheEndGatewayRenderer.class)
public abstract class EndGatewayBeamMixin {
    @WrapWithCondition(method = "render(Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BeaconRenderer;renderBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;FFJIIIFF)V"))
    private boolean nimbus$beam(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float partialTick, float scale, long time, int yOffset,
                                int height, int color, float radius, float glowRadius) {
        NimbusConfig config = NimbusConfig.get();
        return !config.enabled || config.beaconBeams;
    }
}

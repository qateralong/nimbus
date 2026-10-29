package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.Cull;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {
    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void nimbus$text(BlockPos pos, SignText text, PoseStack pose, MultiBufferSource buffers, int light, int lineHeight, int maxWidth, boolean front,
                            CallbackInfo ci) {
        if (!Cull.near(pos, NimbusConfig.get().signTextDistance)) {
            ci.cancel();
            return;
        }
        NimbusConfig config = NimbusConfig.get();
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (config.enabled && config.signBackCulling && minecraft.level != null
                && minecraft.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.SignBlock sign) {
            var state = minecraft.level.getBlockState(pos);
            var center = sign.getSignHitboxCenterPosition(state);
            double dx = Cull.camX - (pos.getX() + center.x);
            double dz = Cull.camZ - (pos.getZ() + center.z);
            if (dx * dx + dz * dz < 0.25) {
                return;
            }
            float angle = (float) (net.minecraft.util.Mth.atan2(dz, dx) * 57.2957763671875) - 90.0F;
            float difference = net.minecraft.util.Mth.degreesDifferenceAbs(sign.getYRotationDegrees(state), angle);
            if (front ? difference > 100.0F : difference < 80.0F) {
                ci.cancel();
            }
        }
    }
}

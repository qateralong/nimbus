package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void nimbus$fog(Camera camera, FogRenderer.FogMode mode, float distance, boolean thick, float partialTick, CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || config.fog && config.fogDistance == 100 || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        if (!config.fog) {
            RenderSystem.setShaderFogStart(1.0e6F);
            RenderSystem.setShaderFogEnd(2.0e6F);
            return;
        }
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            float scale = config.fogDistance / 100.0F;
            RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * scale);
            RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * scale);
        }
    }
}

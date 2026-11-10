package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.Cull;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    @Final
    private Map<ParticleRenderType, Queue<Particle>> particles;

    @Shadow
    @Final
    private Queue<Particle> particlesToAdd;

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private boolean nimbus$cull(Particle particle, VertexConsumer consumer, Camera camera, float partialTick) {
        return Cull.particleVisible(particle);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleRenderType;begin(Lcom/mojang/blaze3d/vertex/Tesselator;Lnet/minecraft/client/renderer/texture/TextureManager;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"))
    private com.mojang.blaze3d.vertex.BufferBuilder nimbus$begin(ParticleRenderType type, com.mojang.blaze3d.vertex.Tesselator tesselator,
                                                                 net.minecraft.client.renderer.texture.TextureManager textures,
                                                                 com.llamalad7.mixinextras.injector.wrapoperation.Operation<com.mojang.blaze3d.vertex.BufferBuilder> original) {
        com.mojang.blaze3d.vertex.BufferBuilder builder = original.call(type, tesselator, textures);
        com.qateralong.nimbus.render.ParticleInstances.begin(type, builder);
        return builder;
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;build()Lcom/mojang/blaze3d/vertex/MeshData;"))
    private com.mojang.blaze3d.vertex.MeshData nimbus$flush(com.mojang.blaze3d.vertex.BufferBuilder builder,
                                                            com.llamalad7.mixinextras.injector.wrapoperation.Operation<com.mojang.blaze3d.vertex.MeshData> original) {
        com.qateralong.nimbus.render.ParticleInstances.flush();
        return original.call(builder);
    }

    // particles.ordering of a shader pack: opaque and translucent sheets drawn at different points of the frame
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object nimbus$sheet(Map<?, ?> map, Object type, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> original) {
        com.qateralong.nimbus.pack.Pipeline p = com.qateralong.nimbus.pack.Pipeline.get();
        if (p != null && !p.particleSheet((ParticleRenderType) type)) {
            return null;
        }
        return original.call(map, type);
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void nimbus$limit(Particle particle, CallbackInfo ci) {
        if (!Cull.acceptParticle()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void nimbus$count(CallbackInfo ci) {
        int count = this.particlesToAdd.size();
        for (Queue<Particle> queue : this.particles.values()) {
            count += queue.size();
        }
        Cull.particleCount = count;
    }
}

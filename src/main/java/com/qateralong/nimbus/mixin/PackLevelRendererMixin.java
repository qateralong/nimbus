package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.Uniform;
import com.qateralong.nimbus.pack.Pipeline;
import java.nio.FloatBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Frame hooks for a loaded shader pack: G-buffer setup, the phase each world draw belongs to, section offsets.
@Mixin(LevelRenderer.class)
public abstract class PackLevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void nimbus$packBegin(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f frustum,
                                  Matrix4f projection, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null) {
            p.beginWorld(camera, frustum, projection, delta.getGameTimeDeltaPartialTick(false));
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void nimbus$packLayer(RenderType layer, double x, double y, double z, Matrix4f frustum, Matrix4f projection, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            if (layer == RenderType.translucent()) {
                if (p.particleOrder != 0) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    p.phase = Pipeline.Phase.PARTICLES;
                    p.particleSheets = p.particleOrder == 1 ? 1 : 0;
                    mc.particleEngine.render(mc.gameRenderer.lightTexture(), mc.gameRenderer.getMainCamera(), mc.getTimer().getGameTimeDeltaPartialTick(false));
                    p.particleSheets = 0;
                }
                p.beforeTranslucent();
                p.phase = Pipeline.Phase.TRANSLUCENT;
            } else if (layer != RenderType.tripwire()) {
                p.phase = Pipeline.Phase.TERRAIN;
            }
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void nimbus$packLayerDone(RenderType layer, double x, double y, double z, Matrix4f frustum, Matrix4f projection, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            p.phase = Pipeline.Phase.ENTITIES;
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            args = "ldc=blockentities"))
    private void nimbus$packBlockEntities(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            p.phase = Pipeline.Phase.BLOCK_ENTITIES;
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V", ordinal = 0))
    private void nimbus$packBlockEntitiesDone(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            p.phase = Pipeline.Phase.ENTITIES;
        }
    }

    // the pack clears its own buffers (to their clear colours); vanilla's fog-colour clear would land in colortex0
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void nimbus$packClear(int mask, boolean osx, Operation<Void> original) {
        Pipeline p = Pipeline.get();
        original.call(p != null && p.inWorld() ? mask & ~org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT : mask, osx);
    }

    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"))
    private void nimbus$packOffset(Uniform uniform, Operation<Void> original) {
        Pipeline p = Pipeline.get();
        if (p != null && "ChunkOffset".equals(uniform.getName())) {
            FloatBuffer b = uniform.getFloatBuffer();
            if (p.chunkOffset(b.get(0), b.get(1), b.get(2))) {
                return;
            }
        }
        original.call(uniform);
    }

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void nimbus$packSky(CallbackInfo ci) {
        phase(Pipeline.Phase.SKY);
    }

    @Inject(method = "renderSky", at = @At("RETURN"))
    private void nimbus$packSkyDone(CallbackInfo ci) {
        phase(Pipeline.Phase.NONE);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void nimbus$packClouds(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld() && !p.clouds) {
            ci.cancel();
            return;
        }
        phase(Pipeline.Phase.CLOUDS);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void nimbus$packSunMoon(com.mojang.blaze3d.vertex.MeshData mesh, Operation<Void> original) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld() && p.skipSkyDraw()) {
            mesh.close();
            return;
        }
        original.call(mesh);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void nimbus$packCloudsDone(CallbackInfo ci) {
        phase(Pipeline.Phase.NONE);
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"))
    private void nimbus$packWeather(CallbackInfo ci) {
        phase(Pipeline.Phase.WEATHER);
    }

    @Inject(method = "renderSnowAndRain", at = @At("RETURN"))
    private void nimbus$packWeatherDone(CallbackInfo ci) {
        phase(Pipeline.Phase.NONE);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V"))
    private void nimbus$packParticles(ParticleEngine engine, LightTexture light, Camera camera, float partial, Operation<Void> original) {
        Pipeline p = Pipeline.get();
        int order = p != null && p.inWorld() ? p.particleOrder : 0;
        if (order == 2) {
            return;
        }
        phase(Pipeline.Phase.PARTICLES);
        if (order == 1) {
            p.particleSheets = 2;
        }
        original.call(engine, light, camera, partial);
        if (p != null) {
            p.particleSheets = 0;
        }
        phase(Pipeline.Phase.NONE);
    }

    private static void phase(Pipeline.Phase phase) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            p.phase = phase;
        }
    }
}

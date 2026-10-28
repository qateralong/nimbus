package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class SkyPartsMixin {
    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V", ordinal = 1))
    private boolean nimbus$sun(MeshData mesh) {
        return keep(NimbusConfig.get().sunMoon, mesh);
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V", ordinal = 2))
    private boolean nimbus$moon(MeshData mesh) {
        return keep(NimbusConfig.get().sunMoon, mesh);
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V", ordinal = 1))
    private boolean nimbus$stars(VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderInstance shader) {
        NimbusConfig config = NimbusConfig.get();
        return !config.enabled || config.stars;
    }

    private static boolean keep(boolean option, MeshData mesh) {
        if (!NimbusConfig.get().enabled || option) {
            return true;
        }
        mesh.close();
        return false;
    }
}

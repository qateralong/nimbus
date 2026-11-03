package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

// Clouds are meshed white and tinted through ColorModulator, so the day/night colour drift no longer rebuilds the mesh.
@Mixin(LevelRenderer.class)
public abstract class CloudColorMixin {
    @Unique
    private static final Vec3 NIMBUS_WHITE = new Vec3(1.0, 1.0, 1.0);

    @Unique
    private Vec3 nimbus$cloudColor = NIMBUS_WHITE;

    @Unique
    private boolean nimbus$tinted;

    @WrapOperation(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getCloudColor(F)Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 nimbus$color(ClientLevel level, float partialTick, Operation<Vec3> original) {
        Vec3 color = original.call(level, partialTick);
        this.nimbus$cloudColor = color;
        return color;
    }

    @Redirect(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"), require = 0)
    private double nimbus$colorChanged(Vec3 color, Vec3 previous) {
        boolean tinted = NimbusConfig.get().fastClouds;
        if (tinted != this.nimbus$tinted) {
            this.nimbus$tinted = tinted;
            return Double.MAX_VALUE;
        }
        return tinted ? 0.0 : color.distanceToSqr(previous);
    }

    @ModifyArg(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;buildClouds(Lcom/mojang/blaze3d/vertex/Tesselator;DDDLnet/minecraft/world/phys/Vec3;)Lcom/mojang/blaze3d/vertex/MeshData;"), index = 4, require = 0)
    private Vec3 nimbus$white(Vec3 color) {
        return this.nimbus$tinted ? NIMBUS_WHITE : color;
    }

    @WrapOperation(method = "renderClouds", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"), require = 0)
    private void nimbus$tint(VertexBuffer buffer, Matrix4f pose, Matrix4f projection, ShaderInstance shader, Operation<Void> original) {
        if (!this.nimbus$tinted) {
            original.call(buffer, pose, projection, shader);
            return;
        }
        float[] current = RenderSystem.getShaderColor();
        float r = current[0];
        float g = current[1];
        float b = current[2];
        float a = current[3];
        Vec3 color = this.nimbus$cloudColor;
        RenderSystem.setShaderColor(r * (float) color.x, g * (float) color.y, b * (float) color.z, a);
        try {
            original.call(buffer, pose, projection, shader);
        } finally {
            RenderSystem.setShaderColor(r, g, b, a);
        }
    }
}

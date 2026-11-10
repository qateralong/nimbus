package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Section buffers get their GL buffers and vertex array on first real use: with our engine they serve only as
// handles, and a 16-chunk view area has 130 000 of them.
@Mixin(VertexBuffer.class)
public abstract class LazyBufferMixin {
    @Shadow
    private int vertexBufferId;

    @Shadow
    private int indexBufferId;

    @Shadow
    private int arrayObjectId;

    @Unique
    private boolean nimbus$lazy;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int nimbus$noBuffer(Operation<Integer> original) {
        return com.qateralong.nimbus.render.LazyBuffers.creatingSections ? 0 : original.call();
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int nimbus$noArray(Operation<Integer> original) {
        return com.qateralong.nimbus.render.LazyBuffers.creatingSections ? 0 : original.call();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$mark(CallbackInfo ci) {
        this.nimbus$lazy = com.qateralong.nimbus.render.LazyBuffers.creatingSections;
    }

    @Inject(method = {"bind", "uploadVertexBuffer", "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder$Result;)V",
            "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/MeshData$DrawState;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$AutoStorageIndexBuffer;"},
            at = @At("HEAD"))
    private void nimbus$create(CallbackInfo ci) {
        if (this.nimbus$lazy && this.arrayObjectId == 0) {
            this.nimbus$lazy = false;
            this.vertexBufferId = GlStateManager._glGenBuffers();
            this.indexBufferId = GlStateManager._glGenBuffers();
            this.arrayObjectId = GlStateManager._glGenVertexArrays();
        }
    }
}

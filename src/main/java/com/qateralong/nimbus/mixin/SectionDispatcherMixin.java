package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.render.Terrain;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.class)
public abstract class SectionDispatcherMixin {
    @Shadow
    @Final
    private Queue<Runnable> toUpload;

    @Shadow
    private volatile boolean closed;

    @Inject(method = "uploadSectionLayer", at = @At("HEAD"), cancellable = true)
    private void nimbus$layer(MeshData mesh, VertexBuffer buffer, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (this.closed) {
            return;
        }
        CompletableFuture<Void> future = Terrain.uploadLayer(mesh, buffer, this.toUpload);
        if (future != null) {
            cir.setReturnValue(future);
        }
    }

    @Inject(method = "uploadSectionIndexBuffer", at = @At("HEAD"), cancellable = true)
    private void nimbus$indices(ByteBufferBuilder.Result result, VertexBuffer buffer, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (this.closed) {
            return;
        }
        CompletableFuture<Void> future = Terrain.uploadIndices(result, buffer, this.toUpload);
        if (future != null) {
            cir.setReturnValue(future);
        }
    }
}

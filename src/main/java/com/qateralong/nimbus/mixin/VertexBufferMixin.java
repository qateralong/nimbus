package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.render.Stream;
import com.qateralong.nimbus.render.StreamTarget;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements StreamTarget {
    @Shadow
    @Final
    private VertexBuffer.Usage usage;

    @Shadow
    private VertexFormat format;

    @Shadow
    private int indexCount;

    @Shadow
    private VertexFormat.Mode mode;

    @Shadow
    protected abstract VertexFormat.IndexType getIndexType();

    @Unique
    private int nimbus$baseVertex = -1;

    @Unique
    private boolean nimbus$streamState;

    @Unique
    private boolean nimbus$immediate;

    @Override
    public void nimbus$markImmediate() {
        this.nimbus$immediate = true;
    }

    @Inject(method = "uploadVertexBuffer", at = @At("HEAD"), cancellable = true)
    private void nimbus$stream(MeshData.DrawState state, ByteBuffer buffer, CallbackInfoReturnable<VertexFormat> cir) {
        this.nimbus$baseVertex = -1;
        if (!this.nimbus$immediate || this.usage != VertexBuffer.Usage.DYNAMIC || buffer == null || !Stream.available()) {
            this.nimbus$leaveStream();
            return;
        }
        VertexFormat target = state.format();
        int stride = target.getVertexSize();
        long offset = Stream.write(buffer, stride);
        if (offset < 0) {
            this.nimbus$leaveStream();
            return;
        }
        if (!this.nimbus$streamState || !target.equals(this.format)) {
            if (this.format != null) {
                this.format.clearBufferState();
            }
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, Stream.buffer());
            target.setupBufferState();
            this.nimbus$streamState = true;
        }
        this.nimbus$baseVertex = (int) (offset / stride);
        cir.setReturnValue(target);
    }

    @Unique
    private void nimbus$leaveStream() {
        if (this.nimbus$streamState) {
            this.nimbus$streamState = false;
            if (this.format != null) {
                this.format.clearBufferState();
            }
            this.format = null;
        }
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void nimbus$draw(CallbackInfo ci) {
        if (this.nimbus$baseVertex >= 0) {
            GL32.glDrawElementsBaseVertex(this.mode.asGLMode, this.indexCount, this.getIndexType().asGLType, 0L, this.nimbus$baseVertex);
            ci.cancel();
        }
    }
}

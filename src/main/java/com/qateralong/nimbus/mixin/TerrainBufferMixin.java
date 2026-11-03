package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.render.Terrain;
import com.qateralong.nimbus.render.TerrainSlot;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class TerrainBufferMixin implements TerrainSlot {
    @Unique
    private int nimbus$start;

    @Unique
    private int nimbus$count;

    @Unique
    private long nimbus$copy;

    @Unique
    private com.qateralong.nimbus.render.Faces nimbus$faces;

    @Unique
    private int nimbus$section = -1;

    @Unique
    private int nimbus$layer = -1;

    @Override
    public void nimbus$bind(int section, int layer) {
        this.nimbus$section = section;
        this.nimbus$layer = layer;
    }

    @Override
    public int nimbus$section() {
        return this.nimbus$section;
    }

    @Override
    public int nimbus$layer() {
        return this.nimbus$layer;
    }

    @Override
    public com.qateralong.nimbus.render.Faces nimbus$faces() {
        return this.nimbus$faces;
    }

    @Override
    public void nimbus$setFaces(com.qateralong.nimbus.render.Faces faces) {
        this.nimbus$faces = faces;
    }

    @Override
    public int nimbus$start() {
        return this.nimbus$start;
    }

    @Override
    public int nimbus$count() {
        return this.nimbus$count;
    }

    @Override
    public void nimbus$set(int start, int count) {
        this.nimbus$start = start;
        this.nimbus$count = count;
    }

    @Override
    public long nimbus$copy() {
        return this.nimbus$copy;
    }

    @Override
    public void nimbus$setCopy(long address) {
        this.nimbus$copy = address;
    }

    @Shadow
    private int vertexBufferId;

    @Shadow
    private int indexBufferId;

    @Shadow
    private int arrayObjectId;

    // With the own engine on, section buffers never touch their GL objects: create them on first real use
    // (0 = not created yet, -1 = closed), which saves hundreds of thousands of GL objects at high render distance.
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int nimbus$lazyBuffer() {
        return Terrain.active() ? 0 : GlStateManager._glGenBuffers();
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int nimbus$lazyArray() {
        return Terrain.active() ? 0 : GlStateManager._glGenVertexArrays();
    }

    @Inject(method = {"upload", "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder$Result;)V", "bind"}, at = @At("HEAD"))
    private void nimbus$ensure(CallbackInfo ci) {
        if (this.arrayObjectId == 0) {
            this.vertexBufferId = GlStateManager._glGenBuffers();
            this.indexBufferId = GlStateManager._glGenBuffers();
            this.arrayObjectId = GlStateManager._glGenVertexArrays();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$release(CallbackInfo ci) {
        if (this.nimbus$count > 0 || this.nimbus$copy != 0L) {
            Terrain.release(this);
        }
        if (this.arrayObjectId == 0) {
            this.vertexBufferId = -1;
            this.indexBufferId = -1;
            this.arrayObjectId = -1;
        }
    }
}

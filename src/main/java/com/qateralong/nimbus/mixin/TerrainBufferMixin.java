package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.render.Terrain;
import com.qateralong.nimbus.render.TerrainSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$release(CallbackInfo ci) {
        if (this.nimbus$count > 0 || this.nimbus$copy != 0L) {
            Terrain.release(this);
        }
    }
}

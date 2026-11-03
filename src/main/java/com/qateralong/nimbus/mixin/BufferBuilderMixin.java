package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.render.FastVertices;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements FastVertices {
    @Shadow
    @Final
    private ByteBufferBuilder buffer;

    @Shadow
    private long vertexPointer;

    @Shadow
    private int vertices;

    @Shadow
    @Final
    private VertexFormat format;

    @Shadow
    @Final
    private VertexFormat.Mode mode;

    @Shadow
    @Final
    private int vertexSize;

    @Shadow
    private int elementsToFill;

    @Shadow
    private boolean building;

    @Shadow
    protected abstract void endLastVertex();

    @Override
    public long nimbus$reserve(VertexFormat format, int count) {
        if (count <= 0 || !this.building || this.format != format || this.mode != VertexFormat.Mode.QUADS) {
            return 0L;
        }
        this.endLastVertex();
        if (this.elementsToFill != 0) {
            return 0L;
        }
        long base = this.buffer.reserve(this.vertexSize * count);
        this.vertices += count;
        this.vertexPointer = base + (long) (count - 1) * this.vertexSize;
        return base;
    }
}

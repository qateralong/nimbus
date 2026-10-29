package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

public final class ModelCapture implements VertexConsumer {
    private final TextureAtlasSprite sprite;
    private final List<BakedQuad> quads = new ArrayList<>();
    private final int[] data = new int[32];
    private int vertex;

    public ModelCapture(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public List<BakedQuad> quads() {
        return this.quads;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        int at = this.vertex * 8;
        this.data[at] = Float.floatToRawIntBits(x);
        this.data[at + 1] = Float.floatToRawIntBits(y);
        this.data[at + 2] = Float.floatToRawIntBits(z);
        this.data[at + 3] = -1;
        this.data[at + 6] = 0;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        int at = this.vertex * 8;
        this.data[at + 4] = Float.floatToRawIntBits(this.sprite.getU(u));
        this.data[at + 5] = Float.floatToRawIntBits(this.sprite.getV(v));
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        int at = this.vertex * 8;
        this.data[at + 7] = (int) (x * 127.0F) & 0xFF | ((int) (y * 127.0F) & 0xFF) << 8 | ((int) (z * 127.0F) & 0xFF) << 16;
        if (++this.vertex == 4) {
            this.vertex = 0;
            Direction direction = Direction.getNearest(x, y, z);
            this.quads.add(new BakedQuad(this.data.clone(), -1, direction, this.sprite, true));
        }
        return this;
    }
}

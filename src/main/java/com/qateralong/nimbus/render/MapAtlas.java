package com.qateralong.nimbus.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.NimbusConfig;
import java.util.BitSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MapAtlas {
    private static final boolean IMMEDIATELY_FAST = FabricLoader.getInstance().isModLoaded("immediatelyfast");
    private static final int MAP = 128;
    private static final int PER_ROW = 16;
    private static final int SIZE = MAP * PER_ROW;
    private static final float SCALE = (float) MAP / SIZE;
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath("nimbus", "map_atlas");
    private static final BitSet USED = new BitSet(PER_ROW * PER_ROW);
    private static final Remap REMAP = new Remap();
    private static Texture texture;
    private static RenderType renderType;

    private MapAtlas() {
    }

    public static boolean enabled() {
        NimbusConfig config = NimbusConfig.get();
        return !IMMEDIATELY_FAST && config.enabled && config.mapAtlas;
    }

    private static final class Texture extends AbstractTexture {
        Texture() {
            this.id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(this.id, SIZE, SIZE);
        }

        @Override
        public void load(ResourceManager manager) {
        }
    }

    public static int allocate() {
        if (!enabled() || !RenderSystem.isOnRenderThread()) {
            return -1;
        }
        if (texture == null) {
            texture = new Texture();
            Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
            renderType = RenderType.text(LOCATION);
        }
        int slot = USED.nextClearBit(0);
        if (slot >= PER_ROW * PER_ROW) {
            return -1;
        }
        USED.set(slot);
        return slot;
    }

    public static void release(int slot) {
        if (slot >= 0) {
            USED.clear(slot);
        }
    }

    public static void upload(int slot, NativeImage pixels) {
        if (slot < 0 || texture == null) {
            return;
        }
        RenderSystem.bindTexture(texture.getId());
        pixels.upload(0, slot % PER_ROW * MAP, slot / PER_ROW * MAP, false);
    }

    public static RenderType renderType() {
        return renderType;
    }

    public static VertexConsumer remap(VertexConsumer target, int slot) {
        REMAP.target = target;
        REMAP.u = slot % PER_ROW * SCALE;
        REMAP.v = slot / PER_ROW * SCALE;
        return REMAP;
    }

    private static final class Remap implements VertexConsumer {
        VertexConsumer target;
        float u;
        float v;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.target.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float pu, float pv) {
            this.target.setUv(this.u + pu * SCALE, this.v + pv * SCALE);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int pu, int pv) {
            this.target.setUv1(pu, pv);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int pu, int pv) {
            this.target.setUv2(pu, pv);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.target.setNormal(x, y, z);
            return this;
        }
    }
}

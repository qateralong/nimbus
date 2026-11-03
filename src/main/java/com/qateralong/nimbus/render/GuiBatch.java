package com.qateralong.nimbus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class GuiBatch {
    private static final int PLAIN = 1;
    private static final int COLORED = 2;
    private static final ByteBufferBuilder MEMORY = new ByteBufferBuilder(1 << 16);
    private static int depth;
    private static int drawing;
    private static boolean internal;
    private static boolean blend;
    private static int kind;
    private static ResourceLocation texture;
    private static BufferBuilder builder;
    private static net.minecraft.client.gui.GuiGraphics pendingText;
    public static int merged;
    public static int lastMerged;

    private GuiBatch() {
    }

    public static boolean active() {
        return depth > 0 && !internal;
    }

    public static void begin() {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.guiBatching) {
            depth++;
        }
    }

    public static boolean deferText(net.minecraft.client.gui.GuiGraphics graphics) {
        if (!active()) {
            return false;
        }
        if (pendingText != null && pendingText != graphics) {
            flushText();
        }
        flush();
        pendingText = graphics;
        return true;
    }

    public static void textFlushed(net.minecraft.client.gui.GuiGraphics graphics) {
        if (!internal && pendingText == graphics) {
            pendingText = null;
        }
    }

    private static void flushText() {
        net.minecraft.client.gui.GuiGraphics graphics = pendingText;
        if (graphics == null || internal) {
            return;
        }
        pendingText = null;
        internal = true;
        try {
            graphics.flush();
        } finally {
            internal = false;
        }
    }

    public static void end() {
        if (depth > 0) {
            flushText();
            flush();
            depth--;
        }
    }

    public static void endFrame() {
        drawing = 0;
        items = false;
        flushText();
        if (depth > 0) {
            flush();
            depth = 0;
        }
        lastMerged = merged;
        merged = 0;
    }

    public static boolean blit(ResourceLocation location, Matrix4f pose, float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1,
                               float v2) {
        if (!active()) {
            return false;
        }
        prepare(PLAIN, location);
        builder.addVertex(pose, x1, y1, z).setUv(u1, v1);
        builder.addVertex(pose, x1, y2, z).setUv(u1, v2);
        builder.addVertex(pose, x2, y2, z).setUv(u2, v2);
        builder.addVertex(pose, x2, y1, z).setUv(u2, v1);
        return true;
    }

    public static boolean blit(ResourceLocation location, Matrix4f pose, float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1,
                               float v2, float red, float green, float blue, float alpha) {
        if (!active()) {
            return false;
        }
        prepare(COLORED, location);
        builder.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(red, green, blue, alpha);
        builder.addVertex(pose, x1, y2, z).setUv(u1, v2).setColor(red, green, blue, alpha);
        builder.addVertex(pose, x2, y2, z).setUv(u2, v2).setColor(red, green, blue, alpha);
        builder.addVertex(pose, x2, y1, z).setUv(u2, v1).setColor(red, green, blue, alpha);
        if (blend) {
            internal = true;
            RenderSystem.disableBlend();
            internal = false;
        }
        return true;
    }

    private static void prepare(int next, ResourceLocation location) {
        flushText();
        if (builder != null && (kind != next || !location.equals(texture))) {
            flush();
        }
        if (builder == null) {
            kind = next;
            texture = location;
            builder = new BufferBuilder(MEMORY, VertexFormat.Mode.QUADS, next == PLAIN ? DefaultVertexFormat.POSITION_TEX : DefaultVertexFormat.POSITION_TEX_COLOR);
        } else {
            merged++;
        }
    }

    private static boolean items;
    private static boolean itemsFlat;

    // Item icons wait in the fixed buffers while their count text goes through the shared one; screens draw
    // without depth test, so the icons must reach the screen before any later geometry.
    public static void itemQueued(boolean flat) {
        items = true;
        itemsFlat = flat;
    }

    public static void itemsFlushed() {
        items = false;
    }

    public static boolean itemsPending() {
        return items;
    }

    public static void drawItems(Runnable draw) {
        items = false;
        boolean flat = itemsFlat;
        if (flat) {
            com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        }
        try {
            draw.run();
        } finally {
            if (flat) {
                com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            }
        }
    }

    public static void drawStart() {
        if (drawing++ == 0 && !internal) {
            flushText();
            flush();
        }
    }

    public static void drawEnd() {
        if (drawing > 0) {
            drawing--;
        }
    }

    public static void blendChanged(boolean enabled) {
        if (internal || drawing > 0) {
            blend = enabled;
            return;
        }
        flushText();
        if (builder != null && kind == PLAIN) {
            flush();
        }
        blend = enabled;
    }

    public static void stateChanged() {
        if (internal || drawing > 0) {
            return;
        }
        flushText();
        if (builder != null) {
            flush();
        }
    }

    public static void flush() {
        if (builder == null || internal) {
            return;
        }
        BufferBuilder pending = builder;
        builder = null;
        MeshData mesh = pending.build();
        if (mesh == null) {
            return;
        }
        internal = true;
        try {
            int previousTexture = RenderSystem.getShaderTexture(0);
            ShaderInstance previousShader = RenderSystem.getShader();
            boolean previousBlend = blend;
            RenderSystem.setShaderTexture(0, texture);
            if (kind == PLAIN) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
            } else {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.enableBlend();
            }
            BufferUploader.drawWithShader(mesh);
            if (kind == COLORED && !previousBlend) {
                RenderSystem.disableBlend();
            }
            RenderSystem.setShaderTexture(0, previousTexture);
            if (previousShader != null) {
                RenderSystem.setShader(() -> previousShader);
            }
        } finally {
            internal = false;
        }
    }
}

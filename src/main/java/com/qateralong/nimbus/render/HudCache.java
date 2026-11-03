package com.qateralong.nimbus.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.GuiAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

// The HUD is drawn into its own target at most hudFps times a second (and right after input, slot changes and chat
// messages) and composited every frame. Camera overlays and the crosshair blend with the world, so they stay live.
// Slow frames, open screens and F1 draw directly.
public final class HudCache {
    private static final float[] SAVED_COLOR = new float[4];
    private static TextureTarget target;
    private static long captured;
    private static long lastFrame;
    private static boolean stale = true;
    private static boolean capturing;
    private static boolean broken;
    private static int slot = -1;
    private static boolean debug;
    public static int captures;

    private HudCache() {
    }

    public static boolean capturing() {
        return capturing;
    }

    public static boolean redirect(RenderTarget bound, boolean viewport) {
        if (!capturing || bound == target || bound != Minecraft.getInstance().getMainRenderTarget()) {
            return false;
        }
        target.bindWrite(viewport);
        return true;
    }

    public static void invalidate() {
        stale = true;
    }

    public static void render(Gui gui, GuiGraphics graphics, DeltaTracker delta, Runnable original) {
        Minecraft minecraft = Minecraft.getInstance();
        NimbusConfig config = NimbusConfig.get();
        long now = System.nanoTime();
        long frame = now - lastFrame;
        lastFrame = now;
        long interval = 1_000_000_000L / Math.max(1, config.hudFps);
        if (!config.enabled || !config.hudCache || broken || minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null
                || frame >= interval) {
            stale = true;
            original.run();
            return;
        }
        int selected = minecraft.player.getInventory().selected;
        boolean showDebug = minecraft.getDebugOverlay().showDebugScreen();
        if (selected != slot || showDebug != debug) {
            slot = selected;
            debug = showDebug;
            stale = true;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (target == null || target.width != main.width || target.height != main.height) {
            if (target == null) {
                target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            } else {
                target.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            stale = true;
        }
        RenderSystem.enableDepthTest();
        graphics.pose().pushPose();
        ((GuiAccessor) gui).nimbus$cameraOverlays(graphics, delta);
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        ((GuiAccessor) gui).nimbus$crosshair(graphics, delta);
        graphics.pose().popPose();
        graphics.flush();
        RenderSystem.disableDepthTest();
        if (stale || now - captured >= interval) {
            capture(main, graphics, original);
            captured = now;
            stale = false;
        }
        composite(minecraft, main);
    }

    private static void capture(RenderTarget main, GuiGraphics graphics, Runnable original) {
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        capturing = true;
        GlMirror.overAlpha = true;
        RenderSystem.blendFuncSeparate(GlMirror.srcRgb, GlMirror.dstRgb, GlMirror.srcAlpha, GlMirror.dstAlpha);
        boolean lost;
        try {
            original.run();
            graphics.flush();
            ItemIcons.drawPending();
        } finally {
            GlMirror.overAlpha = false;
            lost = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != target.frameBufferId;
            capturing = false;
        }
        captures++;
        if (lost) {
            // something in the HUD rebinds framebuffers (a minimap, say); part of it went straight to the screen
            broken = true;
            Nimbus.LOGGER.info("Nimbus: HUD cache off, something in the HUD switches framebuffers");
        }
        main.bindWrite(true);
    }

    private static void composite(Minecraft minecraft, RenderTarget main) {
        boolean quiet = GuiBatch.suspend();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            int texture = RenderSystem.getShaderTexture(0);
            System.arraycopy(RenderSystem.getShaderColor(), 0, SAVED_COLOR, 0, 4);
            boolean depth = GlMirror.depth;
            boolean blend = GlMirror.blend;
            int srcRgb = GlMirror.srcRgb;
            int dstRgb = GlMirror.dstRgb;
            int srcAlpha = GlMirror.srcAlpha;
            int dstAlpha = GlMirror.dstAlpha;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            // the target holds premultiplied colour
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            double scale = minecraft.getWindow().getGuiScale();
            float width = (float) (main.width / scale);
            float height = (float) (main.height / scale);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F);
            builder.addVertex(0.0F, height, 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(width, height, 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(width, 0.0F, 0.0F).setUv(1.0F, 1.0F);
            MeshData mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) {
                RenderSystem.disableBlend();
            }
            if (depth) {
                RenderSystem.enableDepthTest();
            }
            RenderSystem.setShaderColor(SAVED_COLOR[0], SAVED_COLOR[1], SAVED_COLOR[2], SAVED_COLOR[3]);
            RenderSystem.setShaderTexture(0, texture);
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
        } finally {
            GuiBatch.resume(quiet);
        }
    }
}

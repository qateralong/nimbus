package com.qateralong.nimbus;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class Scaler {
    private static RenderTarget target;
    private static boolean active;
    private static boolean sharpened;

    private Scaler() {
    }

    private static int fullWidth;
    private static int fullHeight;

    public static int viewportWidth(int width) {
        return active && width == fullWidth ? target.width : width;
    }

    public static int viewportHeight(int height) {
        return active && height == fullHeight ? target.height : height;
    }

    // true once after a frame whose upscale already sharpened the world
    public static boolean takeSharpened() {
        boolean was = sharpened;
        sharpened = false;
        return was;
    }

    public static RenderTarget redirect() {
        return active ? target : null;
    }

    private static double scale() {
        NimbusConfig config = NimbusConfig.get();
        double base = config.renderScale <= 0 || config.renderScale >= 100 ? 1.0 : Math.max(0.25, config.renderScale / 100.0);
        double scale = base * Cull.resolution;
        if (!config.enabled || scale >= 0.999) {
            return 1.0;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.graphicsMode().get() == GraphicsStatus.FABULOUS || Compat.shadersOn()) {
            return 1.0;
        }
        return scale;
    }

    public static boolean shadersActive() {
        return Compat.shadersOn();
    }

    public static void begin() {
        double scale = scale();
        if (scale >= 0.999) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int width = Math.max(1, (int) Math.round(main.width * scale));
        int height = Math.max(1, (int) Math.round(main.height * scale));
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        fullWidth = main.width;
        fullHeight = main.height;
        active = true;
        target.bindWrite(true);
    }

    public static void end() {
        if (!active) {
            return;
        }
        active = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        NimbusConfig config = NimbusConfig.get();
        if (config.sharpness > 0 && Upscale.draw(target, main, config.sharpness / 100.0F, config.sharpUpscale)) {
            sharpened = true;
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT,
                NimbusConfig.get().sharpUpscale ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        main.bindWrite(true);
    }

}

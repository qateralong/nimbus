package com.qateralong.nimbus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class FpsOverlay {
    private static long windowStart;
    private static long worst;
    private static long last;
    private static int shownMin;

    private FpsOverlay() {
    }

    private static final int GRAPH = com.qateralong.nimbus.render.SlowFrames.HISTORY / 2;
    private static final int HEIGHT = 20;

    // the last frames as 1-pixel bars, newest on the right: 20 px = 50 ms, a faint line at 60 FPS
    private static void graph(GuiGraphics graphics, int x, int y) {
        long[] frames = com.qateralong.nimbus.render.SlowFrames.FRAMES;
        int start = com.qateralong.nimbus.render.SlowFrames.frameIndex;
        int count = GRAPH;
        graphics.fill(x, y + HEIGHT - (int) Math.round(16.7 * HEIGHT / 50.0), x + count, y + HEIGHT - (int) Math.round(16.7 * HEIGHT / 50.0) + 1, 0x40FFFFFF);
        for (int i = 0; i < count; i++) {
            long ns = frames[(start - count + i + frames.length) % frames.length];
            if (ns <= 0L) {
                continue;
            }
            double ms = ns / 1e6;
            int h = (int) Math.max(1, Math.min(HEIGHT, Math.round(ms * HEIGHT / 50.0)));
            int color = ms <= 16.7 ? 0xC08EE08E : ms <= 33.4 ? 0xC0F0D060 : 0xE0F07060;
            graphics.fill(x + i, y + HEIGHT - h, x + i + 1, y + HEIGHT, color);
        }
    }

    public static void render(GuiGraphics graphics) {
        NimbusConfig config = NimbusConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (last != 0) {
            worst = Math.max(worst, now - last);
        }
        last = now;
        if (now - windowStart > 1_000_000_000L) {
            shownMin = worst > 0 ? (int) (1e9 / worst) : 0;
            worst = 0;
            windowStart = now;
        }
        if (!config.enabled || config.fpsOverlay == 0 || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        String text = net.minecraft.client.resources.language.I18n.get("nimbus.overlay.fps", minecraft.getFps(), shownMin);
        int width = minecraft.font.width(text);
        int x = config.fpsOverlay == 2 ? graphics.guiWidth() - width - 4 : 4;
        int fps = minecraft.getFps();
        int color = fps >= 60 ? 0x8EE08E : fps >= 30 ? 0xF0D060 : 0xF07060;
        graphics.drawString(minecraft.font, text, x, 4, color, true);
        if (config.fpsGraph) {
            graph(graphics, config.fpsOverlay == 2 ? graphics.guiWidth() - 4 - GRAPH : 4, 15);
        }
    }
}

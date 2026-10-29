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
        graphics.fill(x - 2, 2, x + width + 2, 13, 0x90000000);
        graphics.drawString(minecraft.font, text, x, 4, 0xFFFFFF, false);
    }
}

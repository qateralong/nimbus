package com.qateralong.nimbus;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Idle {
    private static volatile long lastInput = System.currentTimeMillis();
    private static boolean wasQuiet;

    private Idle() {
    }

    public static void input() {
        lastInput = System.currentTimeMillis();
    }

    public static boolean minimized() {
        Minecraft minecraft = Minecraft.getInstance();
        return GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
    }

    public static boolean afk() {
        int minutes = NimbusConfig.get().afkMinutes;
        return minutes > 0 && System.currentTimeMillis() - lastInput > minutes * 60_000L;
    }

    public static int limit(int value) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || System.getProperty("nimbus.bench") != null) {
            return value;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (config.minimizedFps > 0 && minimized()) {
            return Math.min(value, config.minimizedFps);
        }
        if (config.backgroundFps > 0 && !minecraft.isWindowActive()) {
            return Math.min(value, config.backgroundFps);
        }
        if (config.afkFps > 0 && afk()) {
            return Math.min(value, config.afkFps);
        }
        return value;
    }

    public static float volume(float volume) {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || config.backgroundVolume >= 100 || Minecraft.getInstance().isWindowActive()) {
            return volume;
        }
        return volume * config.backgroundVolume / 100.0F;
    }

    public static void tick(Minecraft minecraft) {
        boolean quiet = NimbusConfig.get().enabled && NimbusConfig.get().backgroundVolume < 100 && !minecraft.isWindowActive();
        if (quiet != wasQuiet) {
            wasQuiet = quiet;
            for (var source : net.minecraft.sounds.SoundSource.values()) {
                minecraft.getSoundManager().updateSourceVolume(source, minecraft.options.getSoundSourceVolume(source));
            }
        }
    }
}

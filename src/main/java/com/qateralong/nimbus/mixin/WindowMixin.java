package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.platform.Window;
import com.qateralong.nimbus.NimbusConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V", remap = false))
    private void nimbus$borderless(long window, long monitor, int x, int y, int width, int height, int refresh) {
        boolean borderless = NimbusConfig.get().borderlessFullscreen;
        if (monitor != 0L && borderless) {
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            int[] mx = new int[1];
            int[] my = new int[1];
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(window, 0L, mx[0], my[0], mode == null ? width : mode.width(), mode == null ? height : mode.height(), GLFW.GLFW_DONT_CARE);
            return;
        }
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowMonitor(window, monitor, x, y, width, height, refresh);
    }
}

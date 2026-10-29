package com.qateralong.nimbus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;

public final class Compat {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static final MethodHandle SHADOW_PASS = irisHandle("isRenderingShadowPass");
    private static final MethodHandle SHADERS = irisHandle("isShaderPackInUse");

    private Compat() {
    }

    private static MethodHandle irisHandle(String method) {
        if (!IRIS) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, method, MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Iris API {} not available", method, t);
            return null;
        }
    }

    public static boolean shadowPass() {
        if (SHADOW_PASS == null) {
            return false;
        }
        try {
            return (boolean) SHADOW_PASS.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shadersOn() {
        if (SHADERS == null) {
            return IRIS;
        }
        try {
            return (boolean) SHADERS.invokeExact();
        } catch (Throwable t) {
            return true;
        }
    }
}

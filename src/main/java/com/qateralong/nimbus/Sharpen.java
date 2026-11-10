package com.qateralong.nimbus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

public final class Sharpen {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath("nimbus", "shaders/post/cas.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width;
    private static int height;

    private Sharpen() {
    }

    public static void apply(float partialTick) {
        NimbusConfig config = NimbusConfig.get();
        int strength = config.sharpness;
        if (Scaler.takeSharpened()) {
            return;
        }
        if (!config.enabled || strength <= 0 || failed || Scaler.shadersActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (chain == null) {
            try {
                chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), CHAIN);
            } catch (Exception e) {
                failed = true;
                Nimbus.LOGGER.error("Failed to load sharpening shader", e);
                return;
            }
        }
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();
        if (w != width || h != height) {
            chain.resize(w, h);
            width = w;
            height = h;
        }
        for (PostPass pass : ((PostChainAccessor) chain).nimbus$passes()) {
            pass.getEffect().safeGetUniform("Sharpness").set(strength / 100.0F);
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partialTick);
        minecraft.getMainRenderTarget().bindWrite(true);
    }

    public static void reset() {
        if (chain != null) {
            chain.close();
        }
        chain = null;
        failed = false;
        width = 0;
        height = 0;
    }
}

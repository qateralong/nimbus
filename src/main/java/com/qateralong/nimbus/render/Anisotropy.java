package com.qateralong.nimbus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

// Anisotropic filtering for the block atlas: with mipmaps, floors and walls seen at a shallow angle stay sharp instead of
// sliding to the blurry small levels, and they still do not shimmer. Not in vanilla.
public final class Anisotropy {
    private static final int MAX_ANISOTROPY = 0x84FE;
    private static final int MAX_SUPPORTED = 0x84FF;
    private static Boolean supported;
    private static float limit;
    private static int lastTexture = -1;
    private static int lastLevel = -1;
    private static int frames;

    private Anisotropy() {
    }

    public static boolean supported() {
        if (supported == null) {
            GLCapabilities caps = GL.getCapabilities();
            supported = caps.OpenGL46 || caps.GL_ARB_texture_filter_anisotropic || caps.GL_EXT_texture_filter_anisotropic;
            limit = supported ? GL11.glGetFloat(MAX_SUPPORTED) : 1.0F;
        }
        return supported;
    }

    // once per frame before the world: cheap unless the atlas or the level changed (re-sent now and then after reloads)
    public static void apply() {
        Minecraft minecraft = Minecraft.getInstance();
        int level = NimbusConfig.get().enabled ? NimbusConfig.get().anisotropy : 0;
        if (level <= 0 && lastLevel <= 0 || !supported()) {
            return;
        }
        AbstractTexture atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        int id = atlas.getId();
        if (id == lastTexture && level == lastLevel && ++frames < 600) {
            return;
        }
        frames = 0;
        lastTexture = id;
        lastLevel = level;
        RenderSystem.bindTexture(id);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, MAX_ANISOTROPY, Math.max(1.0F, Math.min(limit, level)));
    }
}

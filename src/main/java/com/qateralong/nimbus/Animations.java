package com.qateralong.nimbus;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

public final class Animations {
    public static final String[] KINDS = {"water", "lava", "fire", "portal", "plants", "other"};

    private Animations() {
    }

    static String kind(ResourceLocation name) {
        String path = name.getPath();
        if (path.contains("water")) {
            return "water";
        }
        if (path.contains("lava") || path.contains("magma")) {
            return "lava";
        }
        if (path.contains("fire") || path.contains("campfire") || path.contains("lantern") || path.contains("torch")) {
            return "fire";
        }
        if (path.contains("portal")) {
            return "portal";
        }
        if (path.contains("kelp") || path.contains("seagrass") || path.contains("sculk") || path.contains("vine") || path.contains("coral")) {
            return "plants";
        }
        return "other";
    }

    public static TextureAtlasSprite.Ticker wrap(ResourceLocation name, TextureAtlasSprite.Ticker ticker) {
        String kind = kind(name);
        return new TextureAtlasSprite.Ticker() {
            @Override
            public void tickAndUpload() {
                NimbusConfig config = NimbusConfig.get();
                if (!config.enabled || config.animatedTextures && !config.disabledAnimations.contains(kind)) {
                    ticker.tickAndUpload();
                }
            }

            @Override
            public void close() {
                ticker.close();
            }
        };
    }
}

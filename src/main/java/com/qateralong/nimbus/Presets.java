package com.qateralong.nimbus;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;

public final class Presets {
    public static final String[] NAMES = {"none", "subtle", "serious", "max"};

    private Presets() {
    }

    public static void apply(String name) {
        NimbusConfig c = NimbusConfig.get();
        Options o = Minecraft.getInstance().options;
        NimbusConfig d = new NimbusConfig();
        c.itemDistance = d.itemDistance;
        c.experienceDistance = d.experienceDistance;
        c.frameDistance = d.frameDistance;
        c.armorStandDistance = d.armorStandDistance;
        c.paintingDistance = d.paintingDistance;
        c.projectileDistance = d.projectileDistance;
        c.vehicleDistance = d.vehicleDistance;
        c.mobDistance = d.mobDistance;
        c.detailDistance = d.detailDistance;
        c.shadowDistance = d.shadowDistance;
        c.nameTagDistance = d.nameTagDistance;
        c.blockEntityDistance = d.blockEntityDistance;
        c.signTextDistance = d.signTextDistance;
        c.frameContentDistance = d.frameContentDistance;
        c.particleDistance = d.particleDistance;
        c.maxParticles = d.maxParticles;
        int mipmaps = o.mipmapLevels().get();
        c.renderScale = 100;
        c.sharpness = 0;
        c.fog = d.fog;
        c.leavesCulling = d.leavesCulling;
        c.lod = d.lod;
        c.lodDistance = d.lodDistance;
        c.anisotropy = d.anisotropy;
        c.sky = c.sunMoon = c.stars = c.weather = c.vignette = c.beaconBeams = c.animatedTextures = c.blockBreakParticles = true;
        c.enabled = true;
        c.menuAnimations = true;
        c.chunkFade = true;
        c.dynamicLights = false;
        switch (name) {
            // everything high, within reason
            case "none" -> {
                o.graphicsMode().set(GraphicsStatus.FANCY);
                o.ambientOcclusion().set(true);
                o.cloudStatus().set(CloudStatus.FANCY);
                o.particles().set(ParticleStatus.ALL);
                o.entityShadows().set(true);
                o.biomeBlendRadius().set(5);
                o.mipmapLevels().set(4);
                o.entityDistanceScaling().set(1.5);
                c.anisotropy = 16;
                c.sharpness = 30;
                c.lod = true;
                c.lodDistance = 64;
                c.dynamicLights = true;
            }
            // for most players: full resolution, detailed graphics, only what nobody notices goes
            case "subtle" -> {
                o.graphicsMode().set(GraphicsStatus.FANCY);
                o.ambientOcclusion().set(true);
                o.cloudStatus().set(CloudStatus.FAST);
                o.particles().set(ParticleStatus.ALL);
                o.entityShadows().set(true);
                o.biomeBlendRadius().set(2);
                o.mipmapLevels().set(4);
                o.entityDistanceScaling().set(1.0);
                c.anisotropy = 8;
                c.sharpness = 30;
                c.leavesCulling = 1;
                c.detailDistance = 48;
                c.nameTagDistance = 32;
                c.signTextDistance = 24;
                c.frameContentDistance = 48;
                c.particleDistance = 64;
                c.dynamicLights = true;
            }
            // steady on a weak machine: 75% resolution, distant terrain, cuts in what is hardly seen
            case "serious" -> {
                o.graphicsMode().set(GraphicsStatus.FANCY);
                o.ambientOcclusion().set(true);
                o.cloudStatus().set(CloudStatus.FAST);
                o.particles().set(ParticleStatus.DECREASED);
                o.entityShadows().set(false);
                o.biomeBlendRadius().set(1);
                o.mipmapLevels().set(2);
                o.entityDistanceScaling().set(1.0);
                c.renderScale = 75;
                c.sharpness = 85;
                c.anisotropy = 4;
                c.lod = true;
                c.lodDistance = 32;
                c.vignette = false;
                c.leavesCulling = 2;
                c.itemDistance = 24;
                c.experienceDistance = 24;
                c.detailDistance = 24;
                c.shadowDistance = 16;
                c.nameTagDistance = 24;
                c.signTextDistance = 12;
                c.blockEntityDistance = 48;
                c.frameContentDistance = 24;
                c.particleDistance = 40;
                c.maxParticles = 2000;
            }
            // a potato: everything that can go, goes
            case "max" -> {
                o.graphicsMode().set(GraphicsStatus.FAST);
                o.ambientOcclusion().set(false);
                o.cloudStatus().set(CloudStatus.OFF);
                o.particles().set(ParticleStatus.MINIMAL);
                o.entityShadows().set(false);
                o.biomeBlendRadius().set(0);
                o.mipmapLevels().set(0);
                o.entityDistanceScaling().set(0.5);
                c.renderScale = 50;
                c.sharpness = 100;
                c.vignette = false;
                c.sky = false;
                c.sunMoon = false;
                c.stars = false;
                c.weather = false;
                c.animatedTextures = false;
                c.beaconBeams = false;
                c.blockBreakParticles = false;
                c.menuAnimations = false;
                c.chunkFade = false;
                c.leavesCulling = 2;
                scale(c, 0.4);
                c.mobDistance = 48;
                c.maxParticles = 500;
            }
            default -> {
            }
        }
        if (o.mipmapLevels().get() != mipmaps) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.updateMaxMipLevel(o.mipmapLevels().get());
            minecraft.delayTextureReload();
        }
        c.preset = name;
        c.presetBase = name;
        c.save();
        o.save();
    }

    // from fixed reference distances, not the defaults: those are 0 = as in the game
    private static void scale(NimbusConfig c, double factor) {
        c.itemDistance = scaled(40, factor);
        c.experienceDistance = scaled(32, factor);
        c.frameDistance = scaled(56, factor);
        c.armorStandDistance = scaled(64, factor);
        c.paintingDistance = scaled(96, factor);
        c.projectileDistance = scaled(64, factor);
        c.vehicleDistance = scaled(96, factor);
        c.detailDistance = scaled(40, factor);
        c.shadowDistance = scaled(24, factor);
        c.nameTagDistance = scaled(32, factor);
        c.blockEntityDistance = scaled(48, factor);
        c.signTextDistance = scaled(16, factor);
        c.frameContentDistance = scaled(32, factor);
        c.particleDistance = scaled(64, factor);
    }

    private static int scaled(int value, double factor) {
        return value <= 0 ? value : Math.max(8, (int) Math.round(value * factor));
    }
}

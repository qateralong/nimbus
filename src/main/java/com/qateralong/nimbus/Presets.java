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
        c.renderScale = 100;
        c.sharpness = 0;
        c.fog = false;
        c.sky = c.weather = c.vignette = c.beaconBeams = c.animatedTextures = c.blockBreakParticles = true;
        c.enabled = true;
        switch (name) {
            case "none" -> {
                o.graphicsMode().set(GraphicsStatus.FANCY);
                o.ambientOcclusion().set(true);
                o.cloudStatus().set(CloudStatus.FANCY);
                o.particles().set(ParticleStatus.ALL);
                o.entityShadows().set(true);
                o.biomeBlendRadius().set(2);
                o.mipmapLevels().set(4);
                o.entityDistanceScaling().set(1.0);
            }
            case "subtle" -> {
                o.graphicsMode().set(GraphicsStatus.FANCY);
                o.ambientOcclusion().set(true);
                o.cloudStatus().set(CloudStatus.FAST);
                o.particles().set(ParticleStatus.ALL);
                o.entityShadows().set(false);
                o.biomeBlendRadius().set(1);
                o.mipmapLevels().set(2);
                o.entityDistanceScaling().set(1.0);
                c.vignette = false;
                c.itemDistance = 32;
                c.detailDistance = 32;
                c.nameTagDistance = 24;
                c.signTextDistance = 12;
                c.blockEntityDistance = 40;
                c.frameContentDistance = 24;
                c.particleDistance = 48;
                c.maxParticles = 3000;
            }
            case "serious" -> {
                o.graphicsMode().set(GraphicsStatus.FAST);
                o.ambientOcclusion().set(false);
                o.cloudStatus().set(CloudStatus.OFF);
                o.particles().set(ParticleStatus.DECREASED);
                o.entityShadows().set(false);
                o.biomeBlendRadius().set(0);
                o.mipmapLevels().set(0);
                o.entityDistanceScaling().set(0.75);
                c.renderScale = 75;
                c.sharpness = 50;
                c.vignette = false;
                c.weather = false;
                c.animatedTextures = false;
                scale(c, 0.6);
                c.mobDistance = 64;
                c.maxParticles = 1500;
            }
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
                c.sharpness = 70;
                c.vignette = false;
                c.weather = false;
                c.animatedTextures = false;
                c.sky = false;
                c.beaconBeams = false;
                c.blockBreakParticles = false;
                scale(c, 0.4);
                c.mobDistance = 48;
                c.maxParticles = 500;
            }
            default -> {
            }
        }
        c.preset = name;
        c.save();
        o.save();
    }

    private static void scale(NimbusConfig c, double factor) {
        c.itemDistance = scaled(c.itemDistance, factor);
        c.experienceDistance = scaled(c.experienceDistance, factor);
        c.frameDistance = scaled(c.frameDistance, factor);
        c.armorStandDistance = scaled(c.armorStandDistance, factor);
        c.paintingDistance = scaled(c.paintingDistance, factor);
        c.projectileDistance = scaled(c.projectileDistance, factor);
        c.vehicleDistance = scaled(c.vehicleDistance, factor);
        c.detailDistance = scaled(c.detailDistance, factor);
        c.shadowDistance = scaled(c.shadowDistance, factor);
        c.nameTagDistance = scaled(c.nameTagDistance, factor);
        c.blockEntityDistance = scaled(c.blockEntityDistance, factor);
        c.signTextDistance = scaled(c.signTextDistance, factor);
        c.frameContentDistance = scaled(c.frameContentDistance, factor);
        c.particleDistance = scaled(c.particleDistance, factor);
    }

    private static int scaled(int value, double factor) {
        return value <= 0 ? value : Math.max(8, (int) Math.round(value * factor));
    }
}

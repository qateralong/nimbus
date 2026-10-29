package com.qateralong.nimbus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public final class NimbusOptions {
    public static final String[] PAGES = {"general", "quality", "particles", "entities", "blocks", "performance"};

    private NimbusOptions() {
    }

    public interface Entry {
    }

    public record Slider(String key, int min, int max, int step, IntSupplier get, IntConsumer set, String unit) implements Entry {
    }

    public record Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) implements Entry {
    }

    public record Vanilla(AbstractWidgetFactory factory) implements Entry {
    }

    public interface AbstractWidgetFactory {
        AbstractWidget create(Options options, int x, int y, int width);
    }

    private static Vanilla vanilla(net.minecraft.client.OptionInstance<?> option) {
        return new Vanilla(option::createButton);
    }

    public static List<Entry> entries(String tab, NimbusConfig c) {
        Options o = Minecraft.getInstance().options;
        List<Entry> list = new ArrayList<>();
        switch (tab) {
            case "general" -> {
                list.add(new Toggle("enabled", () -> c.enabled, v -> c.enabled = v));
                if (com.qateralong.nimbus.render.Terrain.supported()) {
                    list.add(new Slider("render_engine", 0, 1, 1, () -> c.terrainRenderer ? 1 : 0, v -> {
                        if (c.terrainRenderer != (v == 1)) {
                            c.terrainRenderer = v == 1;
                            if (c == NimbusConfig.get()) {
                                com.qateralong.nimbus.render.Terrain.toggled();
                            }
                        }
                    }, "engine"));
                    if (c.terrainRenderer) {
                        list.add(new Toggle("nimbus_mesher", () -> c.nimbusMesher, v -> {
                            c.nimbusMesher = v;
                            if (c == NimbusConfig.get()) {
                                com.qateralong.nimbus.render.Terrain.toggled();
                            }
                        }));
                    }
                }
                list.add(vanilla(o.renderDistance()));
                list.add(vanilla(o.simulationDistance()));
                list.add(vanilla(o.framerateLimit()));
                list.add(vanilla(o.enableVsync()));
                list.add(vanilla(o.fullscreen()));
                list.add(new Toggle("borderless", () -> c.borderlessFullscreen, v -> c.borderlessFullscreen = v));
                list.add(vanilla(o.guiScale()));
                list.add(vanilla(o.gamma()));
                list.add(vanilla(o.bobView()));
                list.add(vanilla(o.fov()));
                list.add(new Toggle("debug_line", () -> c.debugLine, v -> c.debugLine = v));
                list.add(new Toggle("steady_debug", () -> c.steadyDebug, v -> c.steadyDebug = v));
                list.add(new Toggle("toasts", () -> c.toasts, v -> c.toasts = v));
                list.add(new Slider("fps_overlay", 0, 2, 1, () -> c.fpsOverlay, v -> c.fpsOverlay = v, "corner"));
            }
            case "quality" -> {
                list.add(vanilla(o.graphicsMode()));
                list.add(vanilla(o.ambientOcclusion()));
                list.add(vanilla(o.biomeBlendRadius()));
                list.add(vanilla(o.mipmapLevels()));
                list.add(vanilla(o.cloudStatus()));
                list.add(vanilla(o.entityShadows()));
                list.add(new Slider("render_scale", 25, 100, 5, () -> c.renderScale, v -> c.renderScale = v, "%"));
                list.add(new Slider("sharpness", 0, 100, 5, () -> c.sharpness, v -> c.sharpness = v, "%"));
                list.add(new Toggle("sharp_upscale", () -> c.sharpUpscale, v -> c.sharpUpscale = v));
                list.add(new Toggle("sky", () -> c.sky, v -> c.sky = v));
                list.add(new Toggle("fog", () -> c.fog, v -> c.fog = v));
                list.add(new Toggle("weather", () -> c.weather, v -> c.weather = v));
                list.add(new Toggle("vignette", () -> c.vignette, v -> c.vignette = v));
                list.add(new Toggle("beacon_beams", () -> c.beaconBeams, v -> c.beaconBeams = v));
                list.add(new Toggle("enchanting_book", () -> c.enchantingBook, v -> c.enchantingBook = v));
                list.add(new Toggle("piston_animation", () -> c.pistonAnimation, v -> c.pistonAnimation = v));
                list.add(new Toggle("instant_sneak", () -> c.instantSneak, v -> c.instantSneak = v));
                list.add(new Toggle("animated_textures", () -> c.animatedTextures, v -> c.animatedTextures = v));
                for (String kind : Animations.KINDS) {
                    list.add(new Toggle("anim." + kind, () -> !c.disabledAnimations.contains(kind), v -> {
                        c.disabledAnimations.remove(kind);
                        if (!v) {
                            c.disabledAnimations.add(kind);
                        }
                    }));
                }
                list.add(new Toggle("sun_moon", () -> c.sunMoon, v -> c.sunMoon = v));
                list.add(new Toggle("stars", () -> c.stars, v -> c.stars = v));
                list.add(new Slider("fog_distance", 10, 300, 10, () -> c.fogDistance, v -> c.fogDistance = v, "%"));
                list.add(new Slider("cloud_height", 0, 320, 8, () -> c.cloudHeight, v -> c.cloudHeight = v, "blocks"));
                list.add(vanilla(o.glintStrength()));
                list.add(vanilla(o.screenEffectScale()));
                list.add(vanilla(o.fovEffectScale()));
            }
            case "particles" -> {
                list.add(vanilla(o.particles()));
                list.add(new Toggle("particle_culling", () -> c.particleCulling, v -> c.particleCulling = v));
                list.add(new Slider("particle", 0, 128, 4, () -> c.particleDistance, v -> c.particleDistance = v, "blocks"));
                list.add(new Slider("max_particles", 0, 16000, 500, () -> c.maxParticles, v -> c.maxParticles = v, ""));
                list.add(new Toggle("block_break_particles", () -> c.blockBreakParticles, v -> c.blockBreakParticles = v));
                List<String> types = new ArrayList<>();
                net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet().forEach(key -> types.add(key.toString()));
                types.sort(null);
                for (String type : types) {
                    list.add(new Toggle("particle_type:" + type, () -> !c.disabledParticles.contains(type), v -> {
                        c.disabledParticles.remove(type);
                        if (!v) {
                            c.disabledParticles.add(type);
                        }
                    }));
                }
            }
            case "entities" -> {
                list.add(vanilla(o.entityDistanceScaling()));
                list.add(new Toggle("render_frames", () -> c.renderFrames, v -> c.renderFrames = v));
                list.add(new Toggle("render_armor_stands", () -> c.renderArmorStands, v -> c.renderArmorStands = v));
                list.add(new Toggle("render_paintings", () -> c.renderPaintings, v -> c.renderPaintings = v));
                list.add(new Toggle("section_culling", () -> c.sectionCulling, v -> c.sectionCulling = v));
                list.add(new Toggle("occlusion_culling", () -> c.occlusionCulling, v -> c.occlusionCulling = v));
                list.add(new Slider("item", 0, 128, 4, () -> c.itemDistance, v -> c.itemDistance = v, "blocks"));
                list.add(new Slider("experience", 0, 128, 4, () -> c.experienceDistance, v -> c.experienceDistance = v, "blocks"));
                list.add(new Slider("frame", 0, 160, 4, () -> c.frameDistance, v -> c.frameDistance = v, "blocks"));
                list.add(new Slider("armor_stand", 0, 160, 4, () -> c.armorStandDistance, v -> c.armorStandDistance = v, "blocks"));
                list.add(new Slider("painting", 0, 192, 8, () -> c.paintingDistance, v -> c.paintingDistance = v, "blocks"));
                list.add(new Slider("projectile", 0, 160, 4, () -> c.projectileDistance, v -> c.projectileDistance = v, "blocks"));
                list.add(new Slider("vehicle", 0, 192, 8, () -> c.vehicleDistance, v -> c.vehicleDistance = v, "blocks"));
                list.add(new Slider("mob", 0, 192, 8, () -> c.mobDistance, v -> c.mobDistance = v, "blocks"));
                list.add(new Slider("other", 0, 192, 8, () -> c.otherDistance, v -> c.otherDistance = v, "blocks"));
                list.add(new Slider("detail", 0, 128, 4, () -> c.detailDistance, v -> c.detailDistance = v, "blocks"));
                list.add(new Slider("shadow", 0, 64, 2, () -> c.shadowDistance, v -> c.shadowDistance = v, "blocks"));
                list.add(new Slider("name_tag", 0, 64, 2, () -> c.nameTagDistance, v -> c.nameTagDistance = v, "blocks"));
            }
            case "blocks" -> {
                list.add(new Slider("block_entity", 0, 128, 4, () -> c.blockEntityDistance, v -> c.blockEntityDistance = v, "blocks"));
                list.add(new Slider("sign_text", 0, 64, 2, () -> c.signTextDistance, v -> c.signTextDistance = v, "blocks"));
                list.add(new Slider("frame_content", 0, 128, 4, () -> c.frameContentDistance, v -> c.frameContentDistance = v, "blocks"));
                list.add(new Slider("block_entity_tick", 0, 160, 4, () -> c.blockEntityTickDistance, v -> c.blockEntityTickDistance = v, "blocks"));
                list.add(new Slider("leaves_culling", 0, 2, 1, () -> c.leavesCulling, v -> {
                    if (c.leavesCulling != v) {
                        c.leavesCulling = v;
                        refresh(c);
                    }
                }, "leaves"));
                list.add(new Toggle("beacon_culling", () -> c.beaconCulling, v -> c.beaconCulling = v));
                list.add(new Toggle("chest_models", () -> c.chestModels, v -> {
                    c.chestModels = v;
                    Chests.reset();
                    refresh(c);
                }));
                list.add(new Toggle("bed_models", () -> c.bedModels, v -> {
                    c.bedModels = v;
                    refresh(c);
                }));
                list.add(new Toggle("shulker_models", () -> c.shulkerModels, v -> {
                    c.shulkerModels = v;
                    Chests.reset();
                    refresh(c);
                }));
                list.add(new Toggle("sign_models", () -> c.signModels, v -> {
                    c.signModels = v;
                    refresh(c);
                }));
                list.add(new Toggle("bell_models", () -> c.bellModels, v -> {
                    c.bellModels = v;
                    Chests.reset();
                    refresh(c);
                }));
                list.add(new Toggle("pot_models", () -> c.potModels, v -> {
                    c.potModels = v;
                    Chests.reset();
                    refresh(c);
                }));
                list.add(new Toggle("sign_back_culling", () -> c.signBackCulling, v -> c.signBackCulling = v));
            }
            default -> {
                list.add(new Slider("render_ahead", 0, 4, 1, () -> c.renderAhead, v -> c.renderAhead = v, "frames"));
                list.add(new Slider("background_fps", 0, 120, 5, () -> c.backgroundFps, v -> c.backgroundFps = v, "fps"));
                list.add(new Slider("minimized_fps", 0, 30, 1, () -> c.minimizedFps, v -> c.minimizedFps = v, "fps"));
                list.add(new Slider("afk_minutes", 0, 30, 1, () -> c.afkMinutes, v -> c.afkMinutes = v, "min"));
                list.add(new Slider("afk_fps", 1, 60, 1, () -> c.afkFps, v -> c.afkFps = v, "fps"));
                list.add(new Slider("background_volume", 0, 100, 5, () -> c.backgroundVolume, v -> c.backgroundVolume = v, "%"));
                list.add(vanilla(o.prioritizeChunkUpdates()));
                list.add(new Slider("target_fps", 0, 240, 10, () -> c.targetFps, v -> c.targetFps = v, "fps"));
                list.add(new Toggle("adaptive_resolution", () -> c.adaptiveResolution, v -> c.adaptiveResolution = v));
                list.add(new Toggle("hidden_tick", () -> c.hiddenTickLod, v -> c.hiddenTickLod = v));
                list.add(new Slider("entity_tick", 0, 160, 4, () -> c.entityTickDistance, v -> c.entityTickDistance = v, "blocks"));
                list.add(new Toggle("fast_states", () -> c.fastStates, v -> c.fastStates = v));
                list.add(new Toggle("dedup_models", () -> c.dedupModels, v -> c.dedupModels = v));
                list.add(new Toggle("stream_uploads", () -> c.streamUploads, v -> c.streamUploads = v));
                list.add(new Toggle("gui_batching", () -> c.guiBatching, v -> c.guiBatching = v));
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                    list.add(new Toggle("sodium_graph_cache", () -> c.sodiumGraphCache, v -> c.sodiumGraphCache = v));
                }
                list.add(new Toggle("biome_cache", () -> c.biomeCache, v -> c.biomeCache = v));
                list.add(new Toggle("entity_light_cache", () -> c.entityLightCache, v -> c.entityLightCache = v));
                list.add(new Toggle("entity_grouping", () -> c.entityGrouping, v -> c.entityGrouping = v));
                list.add(new Toggle("entity_batching", () -> c.entityBatching, v -> c.entityBatching = v));
                list.add(new Toggle("map_atlas", () -> c.mapAtlas, v -> c.mapAtlas = v));
                list.add(new Toggle("particle_light_cache", () -> c.particleLightCache, v -> c.particleLightCache = v));
                list.add(new Toggle("weather_cache", () -> c.weatherCache, v -> c.weatherCache = v));
                list.add(new Toggle("fluid_cache", () -> c.fluidCache, v -> c.fluidCache = v));
                if (com.qateralong.nimbus.render.Terrain.supported()) {
                    list.add(new Toggle("section_light_cache", () -> c.sectionLightCache, v -> c.sectionLightCache = v));
                }
            }
        }
        return list;
    }

    private static void refresh(NimbusConfig c) {
        if (c == NimbusConfig.get() && Minecraft.getInstance().levelRenderer != null) {
            Minecraft.getInstance().levelRenderer.allChanged();
        }
    }

    public static Component label(String key) {
        if (key.startsWith("particle_type:")) {
            String id = key.substring("particle_type:".length());
            return Component.literal(id.startsWith("minecraft:") ? id.substring(10) : id);
        }
        return Component.translatable("nimbus.option." + key);
    }

    public static Component tip(String key) {
        if (key.startsWith("particle_type:")) {
            return Component.translatable("nimbus.option.particle_type.tip");
        }
        return Component.translatable("nimbus.option." + key + ".tip");
    }

    public static Component format(Slider option, int value) {
        if (option.unit().equals("engine")) {
            return Component.translatable("nimbus.engine." + value);
        }
        if (option.unit().equals("leaves")) {
            return Component.translatable("nimbus.leaves." + value);
        }
        if (option.unit().equals("corner")) {
            return Component.translatable("nimbus.corner." + value);
        }
        if (value == 0 && option.key().equals("cloud_height")) {
            return Component.translatable("nimbus.default");
        }
        if (value == 0 && option.min() == 0 && !option.key().equals("sharpness")) {
            return Component.translatable(List.of("target_fps", "render_ahead", "background_fps", "minimized_fps", "afk_minutes").contains(option.key())
                    ? "options.off" : "nimbus.unlimited");
        }
        return Component.literal(value + switch (option.unit()) {
            case "blocks" -> " " + Component.translatable("nimbus.blocks").getString();
            case "frames" -> " " + Component.translatable("nimbus.frames").getString();
            case "fps" -> " FPS";
            case "min" -> " " + Component.translatable("nimbus.minutes").getString();
            default -> option.unit();
        });
    }
}

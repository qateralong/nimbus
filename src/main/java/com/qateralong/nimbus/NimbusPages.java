package com.qateralong.nimbus;

import com.qateralong.nimbus.api.ModOptions;
import com.qateralong.nimbus.api.Option;
import com.qateralong.nimbus.api.OptionFlag;
import com.qateralong.nimbus.api.OptionGroup;
import com.qateralong.nimbus.api.OptionImpact;
import com.qateralong.nimbus.api.OptionPage;
import com.qateralong.nimbus.api.OptionRegistry;
import com.qateralong.nimbus.api.SliderOption;
import com.qateralong.nimbus.api.ToggleOption;
import com.qateralong.nimbus.api.VanillaOptions;
import com.qateralong.nimbus.render.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NimbusPages {
    private static final NimbusConfig DEFAULTS = new NimbusConfig();

    private NimbusPages() {
    }

    private static NimbusConfig config() {
        return NimbusConfig.get();
    }

    public static void register(OptionRegistry registry) {
        ModOptions mod = registry.mod("nimbus")
                .name(Component.literal("Nimbus"))
                .icon(ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"))
                .onSave(() -> config().save());
        Options o = Minecraft.getInstance().options;

        OptionPage general = page(mod, "general");
        OptionGroup engine = group(general, "engine");
        List<String> presets = new ArrayList<>(List.of(Presets.NAMES));
        presets.add("custom");
        engine.add(Option.<String>cycle("nimbus:preset").name(Component.translatable("nimbus.option.preset"))
                .tooltip(Component.translatable("nimbus.presets.tip"))
                .values(presets)
                .binding(() -> presets.contains(config().preset) ? config().preset : "custom", name -> {
                    if (!name.equals("custom")) {
                        Presets.apply(name);
                    }
                })
                .formatter(name -> Component.translatable("nimbus.preset." + name))
                .instant());
        toggle(engine, "enabled", c -> c.enabled, (c, v) -> c.enabled = v);
        if (Terrain.supported()) {
            engine.add(Option.<Integer>cycle("nimbus:render_engine")
                    .name(label("render_engine"))
                    .tooltip(tip("render_engine"))
                    .values(List.of(0, 1))
                    .binding(() -> config().terrainRenderer ? 1 : 0, v -> {
                        config().terrainRenderer = v == 1;
                        custom();
                    })
                    .formatter(v -> Component.translatable("nimbus.engine." + v))
                    .defaultValue(DEFAULTS.terrainRenderer ? 1 : 0)
                    .afterApply(Terrain::toggled).impact(OptionImpact.HIGH));
            toggle(engine, "nimbus_mesher", c -> c.nimbusMesher, (c, v) -> c.nimbusMesher = v)
                    .afterApply(Terrain::toggled).visible(() -> config().terrainRenderer);
            toggle(engine, "gpu_occlusion", c -> c.gpuOcclusion, (c, v) -> c.gpuOcclusion = v)
                    .visible(() -> config().terrainRenderer).impact(OptionImpact.MEDIUM);
            toggle(engine, "own_traversal", c -> c.ownTraversal, (c, v) -> c.ownTraversal = v)
                    .afterApply(Terrain::toggled).visible(() -> config().terrainRenderer).impact(OptionImpact.MEDIUM);
            toggle(engine, "own_translucent_sort", c -> c.ownTranslucentSort, (c, v) -> c.ownTranslucentSort = v)
                    .visible(() -> config().terrainRenderer);
        }
        OptionGroup display = group(general, "display");
        vanilla(display, "render_distance", o.renderDistance()).impact(OptionImpact.HIGH);
        vanilla(display, "simulation_distance", o.simulationDistance()).impact(OptionImpact.HIGH);
        vanilla(display, "framerate_limit", o.framerateLimit());
        vanilla(display, "vsync", o.enableVsync());
        vanilla(display, "fullscreen", o.fullscreen());
        toggle(display, "borderless", c -> c.borderlessFullscreen, (c, v) -> c.borderlessFullscreen = v);
        vanilla(display, "gui_scale", o.guiScale()).afterApply(() -> Minecraft.getInstance().resizeDisplay());
        vanilla(display, "gamma", o.gamma());
        vanilla(display, "fov", o.fov());
        vanilla(display, "bob_view", o.bobView());
        OptionGroup hud = group(general, "interface");
        toggle(hud, "debug_line", c -> c.debugLine, (c, v) -> c.debugLine = v);
        toggle(hud, "steady_debug", c -> c.steadyDebug, (c, v) -> c.steadyDebug = v);
        toggle(hud, "toasts", c -> c.toasts, (c, v) -> c.toasts = v);
        hud.add(slider("fps_overlay", 0, 2, 1, c -> c.fpsOverlay, (c, v) -> c.fpsOverlay = v, "corner"));

        OptionPage quality = page(mod, "quality");
        OptionGroup graphics = group(quality, "graphics");
        vanilla(graphics, "graphics_mode", o.graphicsMode()).impact(OptionImpact.HIGH);
        vanilla(graphics, "ambient_occlusion", o.ambientOcclusion()).impact(OptionImpact.LOW);
        vanilla(graphics, "biome_blend", o.biomeBlendRadius()).impact(OptionImpact.LOW);
        vanilla(graphics, "mipmap", o.mipmapLevels()).afterApply(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.updateMaxMipLevel(minecraft.options.mipmapLevels().get());
            minecraft.delayTextureReload();
        });
        vanilla(graphics, "clouds", o.cloudStatus()).impact(OptionImpact.LOW);
        vanilla(graphics, "entity_shadows", o.entityShadows()).impact(OptionImpact.LOW);
        OptionGroup resolution = group(quality, "resolution");
        resolution.add(slider("render_scale", 25, 100, 5, c -> c.renderScale, (c, v) -> c.renderScale = v, "%").impact(OptionImpact.HIGH));
        resolution.add(slider("sharpness", 0, 100, 5, c -> c.sharpness, (c, v) -> c.sharpness = v, "%"));
        toggle(resolution, "sharp_upscale", c -> c.sharpUpscale, (c, v) -> c.sharpUpscale = v);
        OptionGroup world = group(quality, "world");
        toggle(world, "sky", c -> c.sky, (c, v) -> c.sky = v);
        toggle(world, "sun_moon", c -> c.sunMoon, (c, v) -> c.sunMoon = v);
        toggle(world, "stars", c -> c.stars, (c, v) -> c.stars = v);
        toggle(world, "fog", c -> c.fog, (c, v) -> c.fog = v);
        world.add(slider("fog_distance", 10, 300, 10, c -> c.fogDistance, (c, v) -> c.fogDistance = v, "%"));
        toggle(world, "weather", c -> c.weather, (c, v) -> c.weather = v);
        world.add(slider("cloud_height", 0, 320, 8, c -> c.cloudHeight, (c, v) -> c.cloudHeight = v, "blocks"));
        OptionGroup effects = group(quality, "effects");
        toggle(effects, "vignette", c -> c.vignette, (c, v) -> c.vignette = v);
        toggle(effects, "beacon_beams", c -> c.beaconBeams, (c, v) -> c.beaconBeams = v);
        toggle(effects, "enchanting_book", c -> c.enchantingBook, (c, v) -> c.enchantingBook = v);
        toggle(effects, "piston_animation", c -> c.pistonAnimation, (c, v) -> c.pistonAnimation = v);
        toggle(effects, "instant_sneak", c -> c.instantSneak, (c, v) -> c.instantSneak = v);
        vanilla(effects, "glint_strength", o.glintStrength());
        vanilla(effects, "screen_effects", o.screenEffectScale());
        vanilla(effects, "fov_effects", o.fovEffectScale());
        OptionGroup animations = group(quality, "animations");
        toggle(animations, "animated_textures", c -> c.animatedTextures, (c, v) -> c.animatedTextures = v);
        for (String kind : Animations.KINDS) {
            toggle(animations, "anim." + kind, c -> !c.disabledAnimations.contains(kind), (c, v) -> {
                c.disabledAnimations.remove(kind);
                if (!v) {
                    c.disabledAnimations.add(kind);
                }
            }).enabled(() -> config().animatedTextures);
        }

        OptionPage particles = page(mod, "particles");
        OptionGroup particleMain = group(particles, "particles");
        vanilla(particleMain, "particles", o.particles()).impact(OptionImpact.MEDIUM);
        toggle(particleMain, "particle_culling", c -> c.particleCulling, (c, v) -> c.particleCulling = v);
        particleMain.add(slider("particle", 0, 128, 4, c -> c.particleDistance, (c, v) -> c.particleDistance = v, "blocks"));
        particleMain.add(slider("max_particles", 0, 16000, 500, c -> c.maxParticles, (c, v) -> c.maxParticles = v, ""));
        toggle(particleMain, "block_break_particles", c -> c.blockBreakParticles, (c, v) -> c.blockBreakParticles = v);
        OptionGroup types = group(particles, "particle_types").collapsed(true);
        List<String> typeIds = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet().forEach(key -> typeIds.add(key.toString()));
        typeIds.sort(null);
        for (String type : typeIds) {
            types.add(Option.toggle("nimbus:particle_type:" + type)
                    .name(Component.literal(type.startsWith("minecraft:") ? type.substring(10) : type))
                    .tooltip(Component.translatable("nimbus.option.particle_type.tip"))
                    .binding(() -> !config().disabledParticles.contains(type), v -> {
                        config().disabledParticles.remove(type);
                        if (!v) {
                            config().disabledParticles.add(type);
                        }
                        custom();
                    })
                    .defaultValue(!DEFAULTS.disabledParticles.contains(type)));
        }

        OptionPage entities = page(mod, "entities");
        OptionGroup visibility = group(entities, "visibility");
        vanilla(visibility, "entity_distance", o.entityDistanceScaling()).impact(OptionImpact.MEDIUM);
        toggle(visibility, "section_culling", c -> c.sectionCulling, (c, v) -> c.sectionCulling = v).impact(OptionImpact.MEDIUM);
        toggle(visibility, "occlusion_culling", c -> c.occlusionCulling, (c, v) -> c.occlusionCulling = v).impact(OptionImpact.MEDIUM);
        toggle(visibility, "render_frames", c -> c.renderFrames, (c, v) -> c.renderFrames = v);
        toggle(visibility, "render_armor_stands", c -> c.renderArmorStands, (c, v) -> c.renderArmorStands = v);
        toggle(visibility, "render_paintings", c -> c.renderPaintings, (c, v) -> c.renderPaintings = v);
        OptionGroup distances = group(entities, "distances");
        distances.add(slider("mob", 0, 192, 8, c -> c.mobDistance, (c, v) -> c.mobDistance = v, "blocks"));
        distances.add(slider("item", 0, 128, 4, c -> c.itemDistance, (c, v) -> c.itemDistance = v, "blocks"));
        distances.add(slider("experience", 0, 128, 4, c -> c.experienceDistance, (c, v) -> c.experienceDistance = v, "blocks"));
        distances.add(slider("projectile", 0, 160, 4, c -> c.projectileDistance, (c, v) -> c.projectileDistance = v, "blocks"));
        distances.add(slider("vehicle", 0, 192, 8, c -> c.vehicleDistance, (c, v) -> c.vehicleDistance = v, "blocks"));
        distances.add(slider("frame", 0, 160, 4, c -> c.frameDistance, (c, v) -> c.frameDistance = v, "blocks"));
        distances.add(slider("armor_stand", 0, 160, 4, c -> c.armorStandDistance, (c, v) -> c.armorStandDistance = v, "blocks"));
        distances.add(slider("painting", 0, 192, 8, c -> c.paintingDistance, (c, v) -> c.paintingDistance = v, "blocks"));
        distances.add(slider("other", 0, 192, 8, c -> c.otherDistance, (c, v) -> c.otherDistance = v, "blocks"));
        distances.add(slider("detail", 0, 128, 4, c -> c.detailDistance, (c, v) -> c.detailDistance = v, "blocks"));
        distances.add(slider("shadow", 0, 64, 2, c -> c.shadowDistance, (c, v) -> c.shadowDistance = v, "blocks"));
        distances.add(slider("name_tag", 0, 64, 2, c -> c.nameTagDistance, (c, v) -> c.nameTagDistance = v, "blocks"));

        OptionPage blocks = page(mod, "blocks");
        OptionGroup blockDistances = group(blocks, "distances");
        blockDistances.add(slider("block_entity", 0, 128, 4, c -> c.blockEntityDistance, (c, v) -> c.blockEntityDistance = v, "blocks").impact(OptionImpact.MEDIUM));
        blockDistances.add(slider("sign_text", 0, 64, 2, c -> c.signTextDistance, (c, v) -> c.signTextDistance = v, "blocks"));
        blockDistances.add(slider("frame_content", 0, 128, 4, c -> c.frameContentDistance, (c, v) -> c.frameContentDistance = v, "blocks"));
        blockDistances.add(slider("block_entity_tick", 0, 160, 4, c -> c.blockEntityTickDistance, (c, v) -> c.blockEntityTickDistance = v, "blocks"));
        OptionGroup models = group(blocks, "models");
        toggle(models, "chest_models", c -> c.chestModels, (c, v) -> c.chestModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS).impact(OptionImpact.HIGH);
        toggle(models, "shulker_models", c -> c.shulkerModels, (c, v) -> c.shulkerModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "bed_models", c -> c.bedModels, (c, v) -> c.bedModels = v).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "sign_models", c -> c.signModels, (c, v) -> c.signModels = v).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "bell_models", c -> c.bellModels, (c, v) -> c.bellModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "pot_models", c -> c.potModels, (c, v) -> c.potModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        OptionGroup culling = group(blocks, "culling");
        culling.add(slider("leaves_culling", 0, 2, 1, c -> c.leavesCulling, (c, v) -> c.leavesCulling = v, "leaves").flags(OptionFlag.RELOAD_CHUNKS).impact(OptionImpact.MEDIUM));
        toggle(culling, "beacon_culling", c -> c.beaconCulling, (c, v) -> c.beaconCulling = v);
        toggle(culling, "sign_back_culling", c -> c.signBackCulling, (c, v) -> c.signBackCulling = v);

        OptionPage performance = page(mod, "performance");
        OptionGroup frames = group(performance, "frame_rate");
        frames.add(slider("target_fps", 0, 240, 10, c -> c.targetFps, (c, v) -> c.targetFps = v, "fps"));
        toggle(frames, "adaptive_resolution", c -> c.adaptiveResolution, (c, v) -> c.adaptiveResolution = v);
        frames.add(slider("render_ahead", 0, 4, 1, c -> c.renderAhead, (c, v) -> c.renderAhead = v, "frames"));
        vanilla(frames, "chunk_updates", o.prioritizeChunkUpdates());
        OptionGroup background = group(performance, "background");
        background.add(slider("background_fps", 0, 120, 5, c -> c.backgroundFps, (c, v) -> c.backgroundFps = v, "fps"));
        background.add(slider("minimized_fps", 0, 30, 1, c -> c.minimizedFps, (c, v) -> c.minimizedFps = v, "fps"));
        background.add(slider("afk_minutes", 0, 30, 1, c -> c.afkMinutes, (c, v) -> c.afkMinutes = v, "min"));
        background.add(slider("afk_fps", 1, 60, 1, c -> c.afkFps, (c, v) -> c.afkFps = v, "fps"));
        background.add(slider("background_volume", 0, 100, 5, c -> c.backgroundVolume, (c, v) -> c.backgroundVolume = v, "%"));
        OptionGroup ticking = group(performance, "ticking");
        toggle(ticking, "hidden_tick", c -> c.hiddenTickLod, (c, v) -> c.hiddenTickLod = v);
        ticking.add(slider("entity_tick", 0, 160, 4, c -> c.entityTickDistance, (c, v) -> c.entityTickDistance = v, "blocks"));
        OptionGroup optimizations = group(performance, "optimizations");
        toggle(optimizations, "fast_states", c -> c.fastStates, (c, v) -> c.fastStates = v);
        toggle(optimizations, "dedup_models", c -> c.dedupModels, (c, v) -> c.dedupModels = v);
        toggle(optimizations, "stream_uploads", c -> c.streamUploads, (c, v) -> c.streamUploads = v);
        toggle(optimizations, "gui_batching", c -> c.guiBatching, (c, v) -> c.guiBatching = v);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            toggle(optimizations, "sodium_graph_cache", c -> c.sodiumGraphCache, (c, v) -> c.sodiumGraphCache = v);
        }
        toggle(optimizations, "biome_cache", c -> c.biomeCache, (c, v) -> c.biomeCache = v);
        toggle(optimizations, "fast_biome_blend", c -> c.fastBiomeBlend, (c, v) -> c.fastBiomeBlend = v);
        toggle(optimizations, "fast_clouds", c -> c.fastClouds, (c, v) -> c.fastClouds = v);
        toggle(optimizations, "entity_light_cache", c -> c.entityLightCache, (c, v) -> c.entityLightCache = v);
        toggle(optimizations, "entity_grouping", c -> c.entityGrouping, (c, v) -> c.entityGrouping = v);
        toggle(optimizations, "entity_batching", c -> c.entityBatching, (c, v) -> c.entityBatching = v);
        toggle(optimizations, "map_atlas", c -> c.mapAtlas, (c, v) -> c.mapAtlas = v);
        toggle(optimizations, "particle_light_cache", c -> c.particleLightCache, (c, v) -> c.particleLightCache = v);
        toggle(optimizations, "weather_cache", c -> c.weatherCache, (c, v) -> c.weatherCache = v);
        toggle(optimizations, "fluid_cache", c -> c.fluidCache, (c, v) -> c.fluidCache = v);
        if (Terrain.supported()) {
            toggle(optimizations, "section_light_cache", c -> c.sectionLightCache, (c, v) -> c.sectionLightCache = v);
        }
    }

    private static OptionPage page(ModOptions mod, String id) {
        return mod.page(id, Component.translatable("nimbus.tab." + id));
    }

    private static OptionGroup group(OptionPage page, String id) {
        return page.group(Component.translatable("nimbus.group." + id));
    }

    private static void custom() {
        config().preset = "custom";
    }

    private static ToggleOption toggle(OptionGroup group, String key, Function<NimbusConfig, Boolean> get, BiConsumer<NimbusConfig, Boolean> set) {
        return group.add(Option.toggle("nimbus:" + key)
                .name(label(key))
                .tooltip(tip(key))
                .binding(() -> get.apply(config()), v -> {
                    set.accept(config(), v);
                    custom();
                })
                .defaultValue(get.apply(DEFAULTS)));
    }

    private static SliderOption slider(String key, int min, int max, int step, Function<NimbusConfig, Integer> get, BiConsumer<NimbusConfig, Integer> set, String unit) {
        return Option.slider("nimbus:" + key)
                .name(label(key))
                .tooltip(tip(key))
                .range(min, max, step)
                .binding(() -> get.apply(config()), v -> {
                    set.accept(config(), v);
                    custom();
                })
                .formatter(value -> format(key, min, unit, value))
                .defaultValue(get.apply(DEFAULTS));
    }

    private static Option<?, ?> vanilla(OptionGroup group, String key, OptionInstance<?> instance) {
        Option<?, ?> option = VanillaOptions.wrap("minecraft:" + key, instance);
        return option == null ? Option.action("minecraft:" + key) : group.add(option);
    }

    public static Component label(String key) {
        return Component.translatable("nimbus.option." + key);
    }

    public static Component tip(String key) {
        return Component.translatable("nimbus.option." + key + ".tip");
    }

    public static Component format(String key, int min, String unit, int value) {
        switch (unit) {
            case "engine" -> {
                return Component.translatable("nimbus.engine." + value);
            }
            case "leaves" -> {
                return Component.translatable("nimbus.leaves." + value);
            }
            case "corner" -> {
                return Component.translatable("nimbus.corner." + value);
            }
            default -> {
            }
        }
        if (value == 0 && key.equals("cloud_height")) {
            return Component.translatable("nimbus.default");
        }
        if (value == 0 && min == 0 && !key.equals("sharpness")) {
            return Component.translatable(List.of("target_fps", "render_ahead", "background_fps", "minimized_fps", "afk_minutes").contains(key)
                    ? "options.off" : "nimbus.unlimited");
        }
        return Component.literal(value + switch (unit) {
            case "blocks" -> " " + Component.translatable("nimbus.blocks").getString();
            case "frames" -> " " + Component.translatable("nimbus.frames").getString();
            case "fps" -> " FPS";
            case "min" -> " " + Component.translatable("nimbus.minutes").getString();
            default -> unit;
        });
    }
}
